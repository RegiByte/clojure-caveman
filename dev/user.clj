;; =============================================================================
;; REPL Development Workflow
;; =============================================================================
;; This namespace is automatically loaded when starting a REPL with the :dev alias.
;; It provides convenience functions for interactive development.
;;
;; Typical workflow:
;; 1. Start REPL: clojure -M:dev -m nrepl.cmdline (or `just nrepl`)
;; 2. Connect your editor (Calva, Cursive, CIDER, etc.)
;; 3. Evaluate: (start-system!)
;; 4. Make code changes
;; 5. Evaluate changed functions directly or (restart-system!)
;; 6. Test at the REPL with helper functions
;; 7. Evaluate: (stop-system!) when done
;;
;; Pro tip: Use rich comment blocks for experiments - they won't execute
;; on file load but can be evaluated manually with your editor.

(ns user
  (:require
   [caveman.system :as system]
   [next.jdbc :as jdbc]))

;; =============================================================================
;; System State Management
;; =============================================================================

(def system
  "Holds the running system map.
   nil when stopped, populated when started.
   
   This is a var (not an atom) because:
   - We only update it from the REPL
   - alter-var-root provides a clean way to manage the root binding
   - The system map itself is immutable"
  nil)

(defn start-system!
  "Starts the application system and stores it in the `system` var.
   
   Idempotent - won't start twice if already running."
  []
  (if system
    (println "Already started bro!")
    (alter-var-root #'system (constantly (system/start-system)))))

(defn stop-system!
  "Stops the running system and clears the `system` var."
  []
  (when system
    (system/stop-system system)
    (alter-var-root #'system (constantly nil))))

(defn restart-system!
  "Convenience function to stop and start the system.
   
   Useful after making changes to system initialization code.
   For most code changes, just re-evaluate the changed function."
  []
  (stop-system!)
  (start-system!))

;; =============================================================================
;; Component Accessors
;; =============================================================================
;; These helper functions provide quick access to system components
;; for REPL experimentation.

(defn server
  "Returns the Jetty server instance."
  []
  (::system/server system))

(defn db
  "Returns the database connection pool.
   
   Usage at REPL:
   (jdbc/execute! (db) [\"SELECT * FROM prehistoric.cave\"])"
  []
  (::system/db system))

(defn env
  "Returns the Dotenv instance for accessing environment variables.
   
   Usage at REPL:
   (.get (env) \"PORT\")"
  []
  (::system/env system))

(defn cookie-store
  "Returns the Ring cookie store instance."
  []
  (::system/cookie-store system))

;; =============================================================================
;; Rich Comment Block - REPL Experiments
;; =============================================================================
;; This code is never executed automatically, but can be evaluated
;; interactively from your editor. Use it as a scratchpad.

(comment
  ;; System lifecycle commands
  (start-system!)
  (stop-system!)
  (restart-system!)
  
  ;; Debug: send to Portal
  (tap> server)

  ;; Inspect system components
  (db)
  (server)
  (env)
  (cookie-store)

  ;; Database queries
  (jdbc/execute!
   (db)
   ["SELECT * FROM prehistoric.cave"])
  
  (jdbc/execute!
   (db)
   ["SELECT * FROM prehistoric.hominid"])

  ;; Experiment with Clojure
  (:keyword nil) ; Safe navigation - returns nil instead of error

  (tap> "from user ns")

  ;; Paren gate, makes it easy to add new forms, prevents paren collapse...
  )
