;; =============================================================================
;; Test System - Isolated Database Testing with Testcontainers
;; =============================================================================
;; This namespace provides test infrastructure for database-dependent tests.
;; It uses Testcontainers to run real PostgreSQL in Docker for tests.
;;
;; Why Testcontainers?
;; - Tests run against real Postgres (not mocks)
;; - Each test gets a fresh database (full isolation)
;; - No need to manage test database state between runs
;; - Works in CI/CD (GitHub Actions, etc.)
;;
;; Architecture:
;; 1. Single Postgres container for all tests (shared, started once)
;; 2. Migrations run once against template database
;; 3. Each test gets a new database cloned from the template (fast!)
;; 4. Test databases are dropped after each test
;;
;; Performance:
;; - Container start: ~5 seconds (once per test suite)
;; - Migration run: ~1 second (once per test suite)
;; - Per-test overhead: ~50ms (CREATE DATABASE from template)
;;
;; Usage in tests:
;; (deftest my-test
;;   (test-system/with-test-db
;;     (fn [db]
;;       ;; db is a fresh, migrated database
;;       (jdbc/execute! db [\"INSERT INTO ...\"])
;;       (is (= ...)))))

(ns caveman.test-system
  (:require [clojure.java.io :as io]
            [next.jdbc :as jdbc])
  (:import (java.util Properties)
           (org.apache.ibatis.migration DataSourceConnectionProvider FileMigrationLoader)
           (org.apache.ibatis.migration.operations UpOperation)
           (org.apache.ibatis.migration.options DatabaseOperationOption)
           (org.testcontainers.containers PostgreSQLContainer)
           (org.testcontainers.containers.wait.strategy Wait)
           (org.testcontainers.utility DockerImageName)))

;; Enable compile-time warnings for Java reflection calls
(set! *warn-on-reflection* true)

;; =============================================================================
;; Testcontainers PostgreSQL Setup
;; =============================================================================

(defn ^:private start-pg-test-container
  "Starts a PostgreSQL container using Testcontainers.
   
   The container:
   - Runs Postgres 17 in Docker
   - Uses a random available port (no conflicts)
   - Waits for Postgres to be ready before returning
   - Returns the container instance for querying connection details"
  []
  (let [container (PostgreSQLContainer.
                   (-> (DockerImageName/parse "postgres")
                       (.withTag "17")))]
    (.start container)
    ;; Wait for Postgres to be ready to accept connections
    (.waitingFor container (Wait/forListeningPort))
    container))

;; Lazy container initialization - starts only once per test suite.
;; 
;; Using delay ensures:
;; - Container starts on first access (not at namespace load)
;; - Container is shared across all tests (fast)
;; - Container is guaranteed to start exactly once (thread-safe)
;; 
;; Registers shutdown hook to stop container when JVM exits.
(defonce ^:private pg-test-container-delay
  (delay
    (let [container (start-pg-test-container)]
      ;; Ensure container stops when tests finish
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. #(PostgreSQLContainer/.close container)))
      container)))

(defn ^:private get-test-db
  "Returns a datasource connected to the test container's default database.
   
   This is used for:
   - Running migrations (once)
   - Creating template databases
   - Creating/dropping test databases"
  []
  (let [container @pg-test-container-delay]
    (jdbc/get-datasource
     {:dbtype   "postgresql"
      :jdbcUrl  (str "jdbc:postgresql://"
                     (PostgreSQLContainer/.getHost container)
                     ":"
                     (PostgreSQLContainer/.getMappedPort container PostgreSQLContainer/POSTGRESQL_PORT)
                     "/"
                     (PostgreSQLContainer/.getDatabaseName container)
                     "?user="
                     (PostgreSQLContainer/.getUsername container)
                     "&password="
                     (PostgreSQLContainer/.getPassword container))})))

;; =============================================================================
;; Database Migrations
;; =============================================================================

(defn ^:private run-migrations
  "Runs all database migrations against the provided database.
   
   Uses MyBatis Migrations (Java library) to:
   - Read migration scripts from migrations/scripts/
   - Read environment config from migrations/environments/development.properties
   - Execute all migrations in order
   
   This is run once against the template database, which is then
   cloned for each test (much faster than running migrations per test)."
  [db]
  (let [scripts-dir (io/file "migrations/scripts")
        env-properties (io/file "migrations/environments/development.properties")]
    (with-open [env-properties-stream (io/input-stream env-properties)]
      (.operate (UpOperation.)
                (DataSourceConnectionProvider. db)
                (FileMigrationLoader.
                 scripts-dir
                 "UTF-8"
                 (doto (Properties.)
                   (.load env-properties-stream)))
                (doto (DatabaseOperationOption.)
                  (.setSendFullScript true)) ; Run entire scripts (not statement-by-statement)
                nil))))

;; Lazy migration execution - runs once per test suite.
;; 
;; Migrations are run against the template database, which is then
;; cloned by CREATE DATABASE ... TEMPLATE ... for each test.
(defonce ^:private migrations-delay
  (delay (run-migrations (get-test-db))))

;; =============================================================================
;; Test Database Isolation
;; =============================================================================

(def ^:private test-counter
  "Atomic counter for generating unique test database names.
   
   Each test gets test_1, test_2, test_3, etc."
  (atom 0))

(defn with-test-db
  "Provides a fresh, migrated database to the test callback.
   
   Flow:
   1. Ensure migrations have run (happens once)
   2. Create a unique database name (test_N)
   3. CREATE DATABASE test_N TEMPLATE <default> (fast - just copies structure)
   4. Run the test callback with a connection to test_N
   5. DROP DATABASE test_N (cleanup)
   
   This ensures complete test isolation - no shared state between tests.
   
   Usage:
   (with-test-db
     (fn [db]
       (jdbc/execute! db [\"INSERT INTO ...\"])
       (is (= ...))))"
  [callback]
  ;; Ensure migrations have run (blocks until complete)
  @migrations-delay

  (let [test-database-name (str "test_" (swap! test-counter inc))
        container          @pg-test-container-delay
        db                 (get-test-db)]
    ;; Create a new database by cloning the template
    ;; This is MUCH faster than running migrations for each test
    (jdbc/execute!
     db
     [(format "CREATE DATABASE %s TEMPLATE %s;"
              test-database-name
              (PostgreSQLContainer/.getDatabaseName container))])

    (try
      ;; Connect to the test database and run the test
      (let [db (jdbc/get-datasource
                {:dbtype   "postgresql"
                 :jdbcUrl  (str "jdbc:postgresql://"
                                (PostgreSQLContainer/.getHost container)
                                ":"
                                (PostgreSQLContainer/.getMappedPort container
                                                                    PostgreSQLContainer/POSTGRESQL_PORT)
                                "/"
                                test-database-name
                                "?user="
                                (PostgreSQLContainer/.getUsername container)
                                "&password="
                                (PostgreSQLContainer/.getPassword container))})]
        (callback db))
      (finally
        ;; Always clean up - drop the test database
        (jdbc/execute! db
                       [(format "DROP DATABASE %s;" test-database-name)])))))
