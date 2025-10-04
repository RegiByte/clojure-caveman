;; =============================================================================
;; Hello World Routes
;; =============================================================================
;; Simple example route that demonstrates:
;; - Database queries with next.jdbc
;; - HTML rendering with Hiccup
;; - Using the system map for dependency injection

(ns caveman.hello.routes
  (:require
   [caveman.page-html.core :as page-html]
   [caveman.system :as-alias system]
   [hiccup2.core :as hiccup]
   [next.jdbc :as jdbc]))

(defn hello-handler
  "Handles GET / - the home page.
   
   Demonstrates:
   - Accessing database from system map
   - Running a simple query
   - Namespace-qualified destructuring (:keys [planet])
   - Rendering HTML with Hiccup
   
   Note: The query \"SELECT 'earth' as planet\" is just a demo.
   Remove the tap> before production!"
  [{::system/keys [db]} request]
  (tap> request) ; Debug: inspect request in Portal (remove in production!)
  (let [{:keys [planet]} (jdbc/execute-one!
                          db
                          ["SELECT 'earth' as planet"])]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (str
            (hiccup/html
             (page-html/view
              :body [:h1 (str "Hello, " planet "!")]
              :title "Something nice")))}))

(defn routes
  "Returns route definitions for hello feature."
  [system]
  [["/" {:get {:handler (partial #'hello-handler system)}}]])
