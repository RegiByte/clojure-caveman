;; =============================================================================
;; Background Job Processing
;; =============================================================================
;; This namespace is the central dispatcher for all background jobs.
;; It uses Proletarian, a PostgreSQL-backed job queue library.
;;
;; Architecture:
;; - Jobs are stored in the proletarian.job table (created by migrations)
;; - Each job has a job_type (keyword) and payload (JSON)
;; - The worker polls for jobs and dispatches them to handlers
;; - Failed jobs are automatically retried with exponential backoff
;;
;; Job creation:
;; - Database triggers (see migrations/scripts/*_trigger.sql)
;; - Manual enqueuing from code
;; - Scheduled tasks (if configured)

(ns caveman.jobs
  (:require [caveman.cave.jobs :as cave-jobs]
            [caveman.system :as-alias system]
            [cheshire.core :as cheshire] ; JSON library
            [clojure.tools.logging :as log]
            [proletarian.protocols :as protocols]
            [proletarian.worker :as-alias worker]))

;; =============================================================================
;; JSON Serialization
;; =============================================================================

(def json-serializer
  "Custom JSON serializer for Proletarian jobs.
   
   Proletarian needs to serialize/deserialize job payloads to/from JSON.
   This implementation uses Cheshire with keyword keys for idiomatic Clojure."
  (reify protocols/Serializer
    (encode [_ data]
      (cheshire/generate-string data))
    (decode [_ data-string]
      ;; Parse with keyword keys: {\"foo\": \"bar\"} -> {:foo \"bar\"}
      (cheshire/parse-string data-string keyword))))

;; =============================================================================
;; Logging Configuration
;; =============================================================================

(defn log-level
  "Maps Proletarian event types to log levels.
   
   Most events are logged at :info level, but errors are logged at :error.
   Polling events are at :debug to reduce log noise."
  [x]
  (case x
    ::worker/queue-worker-shutdown-error :error
    ::worker/handle-job-exception-with-interrupt :error
    ::worker/handle-job-exception :error
    ::worker/job-worker-error :error
    ::worker/polling-for-jobs :debug ; Very frequent, don't spam logs
    :proletarian.retry/not-retrying :error
    :info)) ; Default level

(defn logger
  "Custom logger for Proletarian events.
   
   Proletarian calls this function to log various events during job processing.
   We route them through clojure.tools.logging to respect our logging config."
  [x data]
  (log/logp (log-level x) x data))

;; =============================================================================
;; Job Handlers Registry
;; =============================================================================

(defn handlers
  "Returns a map of all job-type keywords to their handler functions.
   
   Format: {job-type-keyword handler-fn}
   
   Each feature namespace (cave-jobs, etc.) contributes its handlers,
   which are merged together here.
   
   To add new job types:
   1. Create a new namespace (e.g., caveman.user.jobs)
   2. Define handlers function returning a map
   3. Merge it here"
  []
  (-> {}
      (merge (cave-jobs/handlers))
      ;; Add more feature job handlers here:
      ;; (merge (user-jobs/handlers))
      ;; (merge (notification-jobs/handlers))
      ))

;; =============================================================================
;; Job Dispatcher
;; =============================================================================

(defn process-job
  "Main job dispatcher - routes jobs to their handlers based on job-type.
   
   This function is called by the Proletarian worker for each job.
   
   Parameters:
   - system: The system map (database, env, etc.)
   - job-type: Keyword identifying the job type (e.g., :prehistoric.cave/insert)
   - payload: Job data (usually a map, already deserialized from JSON)
   
   If no handler is registered for the job-type, throws an exception.
   This causes the job to be retried (in case of deployment race conditions)."
  [system job-type payload]
  ;; Debug: send job info to Portal for inspection
  (tap> {:job-type job-type
         :payload payload})
  
  (if-let [handler (get (handlers) job-type)]
    ;; Handler found - execute it
    (handler system job-type payload)
    ;; Handler not found - throw exception to retry
    ;; This handles cases where jobs are enqueued but handler isn't deployed yet
    (throw (ex-info "Unhandled Job Type"
                    {:job-type job-type
                     :payload payload}))))
