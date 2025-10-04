;; =============================================================================
;; Cave Routes - CRUD Operations for Cave Management
;; =============================================================================
;; This namespace demonstrates a full-stack feature including:
;; - HTML form rendering with Hiccup
;; - CSRF protection with anti-forgery tokens
;; - Database queries with next.jdbc
;; - Background job triggering via database triggers
;;
;; The cave insert flow:
;; 1. User submits form → cave-create-handler
;; 2. Handler inserts into prehistoric.cave table
;; 3. Database trigger fires → inserts job into proletarian.job table
;; 4. Background worker picks up job → creates a hominid for the cave
;; This pattern decouples the HTTP request from slow/external operations

(ns caveman.cave.routes
  (:require
   [caveman.middlewares :as middlewares]
   [caveman.page-html.core :as page-html]
   [caveman.system :as-alias system]
   [hiccup2.core :as hiccup]
   [next.jdbc :as jdbc]
   [ring.util.anti-forgery :as anti-forgery]
   [ring.util.response :as response]))

(defn cave-create-handler
  "Handles POST /cave/create - creates a new cave.
   
   Flow:
   1. Extracts description from form params
   2. Inserts into database
   3. Database trigger creates a background job
   4. Redirects back to /cave to see the new cave
   
   Note: tap> is used for debugging - sends data to Portal for inspection"
  [{::system/keys [db]
    :as _system} request]
  (tap> request) ; Debug: inspect request in Portal
  (let [{:keys [description]} (:params request)]
    (jdbc/execute!
     db
     ["INSERT INTO prehistoric.cave(description) VALUES(?)"
      description])
    ;; Redirect to GET /cave (Post-Redirect-Get pattern)
    ;; This prevents form resubmission if user refreshes the page
    (response/redirect "/cave")))

(defn cave-handler
  "Handles GET /cave - displays all caves and a creation form.
   
   This demonstrates:
   - Querying the database
   - Rendering HTML with Hiccup
   - CSRF protection with anti-forgery tokens
   - Namespace-qualified keys from database (:cave/id, :cave/description)"
  [{::system/keys [db]
    :as _system} _request]
  (let [caves (jdbc/execute! db ["SELECT id, description from prehistoric.cave"])]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (str
               (hiccup/html
                (page-html/view :body [:div {:id "app"}
                                       [:h1 "Create a new cave!"]
                                       ;; Form with CSRF protection
                                       [:form {:method "post"
                                               :action "/cave/create"}
                                        ;; Anti-forgery token (required by middleware)
                                        ;; hiccup/raw prevents HTML escaping
                                        (hiccup/raw (anti-forgery/anti-forgery-field))
                                        [:label {:for "description"} "Description "]
                                        [:input {:name "description"
                                                 :type "text"}]
                                        [:input {:type "submit"}]]
                                       ;; Display all existing caves
                                       [:ul {}
                                        (for [cave caves]
                                          ;; next.jdbc returns namespace-qualified keys
                                          [:li {} (:cave/id cave) " - " (:cave/description cave)])]])))}))

(defn routes
  "Returns route definitions for cave management.
   
   Reitit route format:
   [path {:middleware [...]} ; optional middleware for these routes only
    [path {:method {:handler fn}}]]
   
   The middleware vector is applied to all routes under this prefix."
  [system]
  [""
   {;; Apply standard HTML middleware to all cave routes
    :middleware (middlewares/standard-html-route-middleware system)}
   [["/cave" {:get {:handler (partial #'cave-handler system)}}]
    ["/cave/create" {:post {:handler (partial #'cave-create-handler system)}}]]])
