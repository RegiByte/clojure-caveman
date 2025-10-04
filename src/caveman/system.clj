;; =============================================================================
;; System Lifecycle Management
;; =============================================================================
;; This namespace manages the entire application lifecycle using the "system map"
;; pattern. The system map is a single Clojure map that holds all stateful
;; components (database, web server, background workers, etc.).
;;
;; Key concepts:
;; - Namespaced keywords (::env, ::db, etc.) prevent key collisions
;; - Each component has start-* and stop-* functions
;; - Components are started in dependency order
;; - Components are stopped in reverse order
;; - System can be stopped and restarted from the REPL for interactive development

(ns caveman.system
  (:require
   [caveman.jobs :as jobs]
   [caveman.routes :as routes]
   [next.jdbc.connection :as connection]
   [proletarian.worker :as worker]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.session.cookie :as session-cookie])
  (:import
   (com.zaxxer.hikari HikariDataSource)
   (io.github.cdimascio.dotenv Dotenv)
   (org.eclipse.jetty.server Server)))

;; Enable compile-time warnings for Java reflection calls (performance optimization)
(set! *warn-on-reflection* true)

;; =============================================================================
;; Environment Configuration
;; =============================================================================

(defn start-env
  "Loads environment variables from .env file in the project root.
   Returns a Dotenv instance that can be queried with (.get env \"VAR_NAME\")."
  []
  (Dotenv/load))

;; =============================================================================
;; Session Management
;; =============================================================================

(defn start-cookie-store
  "Creates a cookie-based session store for Ring middleware.
   Sessions are stored in encrypted cookies sent to the client.
   This is stateless - no server-side session storage needed."
  []
  (session-cookie/cookie-store))

;; =============================================================================
;; Database Connection Pool
;; =============================================================================

(defn start-db
  "Creates a HikariCP connection pool to PostgreSQL.
   HikariCP manages a pool of database connections for better performance.
   
   Takes the system map to access environment configuration.
   Returns a datasource that can be used with next.jdbc."
  [{::keys [env]}]
  (connection/->pool HikariDataSource
                     {:dbtype "postgres"
                      :dbname "postgres"
                      :username (Dotenv/.get env "POSTGRES_USERNAME")
                      :password (Dotenv/.get env "POSTGRES_PASSWORD")
                      :port 5436}))

(defn stop-db
  "Closes the database connection pool and releases all connections."
  [db]
  (HikariDataSource/.close db))

;; =============================================================================
;; Background Job Worker
;; =============================================================================

(defn start-worker
  "Starts a Proletarian background job worker.
   
   The worker:
   - Polls the proletarian.job table in Postgres for new jobs
   - Processes jobs using the jobs/process-job multimethod
   - Retries failed jobs according to configured policy
   - Uses JSON for job serialization
   
   Jobs are created by database triggers (see migrations/scripts/..._trigger.sql)
   This decouples database operations from side effects."
  [{::keys [db]
    :as system}]
  (let [worker (worker/create-queue-worker
                db
                (partial #'jobs/process-job system)
                {:proletarian/log #'jobs/logger
                 :proletarian/serializer jobs/json-serializer})]
    (worker/start! worker)
    worker))

(defn stop-worker
  "Stops the background job worker gracefully.
   Waits for in-flight jobs to complete before shutting down."
  [worker]
  (worker/stop! worker))

;; =============================================================================
;; HTTP Server
;; =============================================================================

(defn start-server
  "Starts the Jetty web server.
   
   Development mode:
   - Uses var indirection (#'routes/root-handler) for hot reloading
   - Router is recompiled on every request to pick up code changes
   
   Production mode:
   - Router is compiled once at startup for better performance
   - Code changes require server restart
   
   The server runs on :join? false so it doesn't block the REPL thread."
  [{::keys [env]
    :as system}]
  (let [handler
        (if (= (Dotenv/.get env "ENVIRONMENT") "development")
          ;; Dev: var indirection enables hot reloading
          (partial #'routes/root-handler system)
          ;; Prod: compile once for performance
          (routes/root-handler system))]
    (jetty/run-jetty
     handler
     {:port (Long/parseLong (Dotenv/.get env "PORT"))
      :join? false}))) ; Don't block - return immediately

(defn stop-server
  "Stops the Jetty web server."
  [server]
  (Server/.stop server))

;; =============================================================================
;; System Lifecycle
;; =============================================================================

(defn start-system
  "Starts all system components in dependency order.
   
   Order matters:
   1. Environment - needed by all other components
   2. Cookie store - stateless, no dependencies
   3. Database - needed by worker and routes
   4. Worker - needs database to poll for jobs
   5. Server - needs database for handlers, starts accepting requests last
   
   Uses threading macros for cleaner composition:
   - Thread-first (->) for the initial env and cookie-store (no dependencies)
   - as-> for db, worker, server (they need to reference the system map)
   
   Returns a system map containing all started components.
   The system map can be passed to stop-system to shut everything down."
  []
  ;; old code, without thread-first
  ;; (let [system-so-far {::env (start-env)}
  ;;       system-so-far (merge system-so-far {::cookie-store (start-cookie-store)})
  ;;       system-so-far (merge system-so-far {::db (start-db system-so-far)})
  ;;       system-so-far (merge system-so-far {::worker (start-worker system-so-far)})
  ;;       system-so-far (merge system-so-far {::server (start-server system-so-far)})]
  ;;   system-so-far)
  (-> {::env (start-env)}
      (assoc ::cookie-store (start-cookie-store))
      ;; as-> is used when we need to pass the accumulator to a function
      (as-> sys (assoc sys ::db (start-db sys)))
      (as-> sys (assoc sys ::worker (start-worker sys)))
      (as-> sys (assoc sys ::server (start-server sys)))))

(defn stop-system
  "Stops all system components in reverse order.
   
   Reverse order ensures graceful shutdown:
   1. Server - stop accepting new requests first
   2. Worker - finish processing background jobs
   3. Database - close all connections last"
  [system]
  (stop-server (::server system))
  (stop-worker (::worker system))
  (stop-db (::db system)))
