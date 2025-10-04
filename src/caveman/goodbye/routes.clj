;; =============================================================================
;; Goodbye Routes
;; =============================================================================
;; Another simple example route.
;; Demonstrates a handler without database access.

(ns caveman.goodbye.routes
  (:require
   [caveman.page-html.core :as page-html]
   [hiccup2.core :as hiccup]))

(defn goodbye-handler
  "Handles GET /goodbye
   
   Simpler than hello-handler - doesn't use the database.
   Still gets the system map (for consistency) but doesn't use it.
   
   Note: Remove tap> before production!"
  [_system request]
  (tap> request) ; Debug: inspect request in Portal (remove in production!)
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (str
          (hiccup/html
           (page-html/view :body [:h1 "Goodbye bro"])))})

(defn routes
  "Returns route definitions for goodbye feature."
  [system]
  [["/goodbye" {:get {:handler (partial #'goodbye-handler system)}}]])
