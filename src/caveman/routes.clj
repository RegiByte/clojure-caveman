;; =============================================================================
;; Root Routing Configuration
;; =============================================================================
;; This namespace defines the top-level router using Reitit.
;; Reitit is a fast, data-driven routing library for Ring.
;;
;; Route organization:
;; - Routes are defined in feature namespaces (hello, goodbye, cave, static)
;; - This namespace composes them into a single router
;; - The router can be compiled once (production) or per-request (development)

(ns caveman.routes
  (:require
   [caveman.cave.routes :as cave-routes]
   [caveman.goodbye.routes :as goodbye-routes]
   [caveman.hello.routes :as hello-routes]
   [caveman.static.routes :as static-routes]
   ; as-alias: use ::system/db without requiring all of system
   ; in fact if we tried to import the system directly here
   ; it would cause a circular reference issue, since the routes are used
   ; to build the server as part of the system initialization
   ; here we just need the namespace reference to extract keys
   ; from the running system
   [caveman.system :as-alias system]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [reitit.ring :as reitit-ring]))

;; Enable compile-time warnings for Java reflection calls
(set! *warn-on-reflection* true)

(defn routes
  "Composes all feature routes into a single route tree.
   
   Routes are data structures in Reitit format:
   [path {:method {:handler fn}}]
   
   The system map is passed to each route namespace so handlers
   can access database, env vars, etc."
  [system]
  [""
   (static-routes/routes system)
   (hello-routes/routes system)
   (goodbye-routes/routes system)
   (cave-routes/routes system)])

(defn not-found-handler
  "Handles 404 Not Found responses for unmatched routes."
  [_request]
  {:status 404
   :headers {"Content-Type" "text/html"}
   :body "Not Found"})

(defn root-handler
  "Creates the main Ring handler for the application.
   
   This function has two arities for different modes:
   
   1-arity (production):
   - Compiles the router once at startup
   - Better performance since routing is cached
   - Requires server restart to pick up route changes
   
   2-arity (development):
   - Compiles the router on every request
   - Allows hot reloading of routes and handlers
   - Slower but more convenient for development"
  
  ;; Dev-only, compiles router for each request
  ([system request]
   ((root-handler system) request))

  ;; Prod-only, compiles once, serve many requests
  ([system]
   (let [handler (reitit-ring/ring-handler
                  (reitit-ring/router
                   (routes system))
                  #'not-found-handler)] ; var indirection for REPL development
     (fn root-handler [request]
       ;; Log every incoming request
       (log/info (str
                  (str/upper-case (:request-method request)) " " (:uri request)))
       (handler request)))))