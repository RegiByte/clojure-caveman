;; =============================================================================
;; Cave Background Jobs
;; =============================================================================
;; This namespace defines background job handlers for cave-related operations.
;;
;; Architecture pattern: Database Triggers → Job Queue → Background Worker
;; 
;; Flow:
;; 1. A cave is inserted into prehistoric.cave table
;; 2. PostgreSQL trigger (see migrations/.../cave_insert_trigger.sql) fires
;; 3. Trigger inserts a job record into proletarian.job table with:
;;    - job_type: ':prehistoric.cave/insert'
;;    - payload: JSON of the inserted cave row
;; 4. Proletarian worker polls the job table
;; 5. Worker calls process-cave-insert with the payload
;; 6. Handler creates a hominid associated with the cave
;;
;; Benefits:
;; - Decouples slow operations from HTTP requests
;; - Automatic retries on failure
;; - Database guarantees job creation (same transaction as cave insert)
;; - Can scale workers independently

(ns caveman.cave.jobs
  (:require
   [caveman.system :as-alias system]
   [honey.sql :as sql] ; Dynamic SQL generation
   [next.jdbc :as jdbc])
  (:import (java.util UUID)))

;; Enable compile-time warnings for Java reflection calls
(set! *warn-on-reflection* true)

(defn process-cave-insert
  "Handles the :prehistoric.cave/insert background job.
   
   This job is triggered automatically when a cave is inserted into the database.
   It creates a hominid inhabitant for the newly created cave.
   
   Parameters:
   - system: The system map (provides access to database)
   - _job-type: The job type keyword (not used, but part of job protocol)
   - payload: Map containing the inserted cave data, including :id
   
   Uses HoneySQL for SQL generation instead of raw SQL strings.
   This provides better composability and safety."
  [{::system/keys [db]} _job-type payload]
  (jdbc/execute!
   db
   ;; Old approach with raw SQL (commented for reference):
   ;; ["INSERT INTO prehistoric.hominid(name, cave_id)
   ;;    VALUES (?, ?)"
   ;;  "Grunk"
   ;;  (UUID/fromString (:id payload))]
   
   ;; New approach with HoneySQL - generates parameterized SQL
   (sql/format
    {:insert-into :prehistoric/hominid
     :values [{:name "Grunker" ; The hominid's name
               :cave_id (UUID/fromString (:id payload))}]})))

(defn handlers
  "Returns a map of job-type keywords to handler functions.
   
   This map is merged into the global job handlers in caveman.jobs.
   Each handler must accept: [system job-type payload]
   
   The job-type matches what's inserted by the database trigger.
   Using var (#') allows REPL hot-reloading of handler implementations."
  []
  {:prehistoric.cave/insert #'process-cave-insert})
