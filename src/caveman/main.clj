;; =============================================================================
;; Application Entry Point
;; =============================================================================
;; This is the main entry point when running the application from the command line
;; with: clojure -M -m caveman.main (or `just run`)
;;
;; For REPL-driven development, use dev/user.clj instead, which provides
;; start-system!, stop-system!, and restart-system! for interactive control.

(ns caveman.main
  (:require
   [caveman.system :as system]))

(defn -main
  "Starts the application system.
   
   This function is called when running the application from the command line.
   The system will run until interrupted (Ctrl+C)."
  []
  (system/start-system))

