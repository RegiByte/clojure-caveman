;; =============================================================================
;; Ring Middleware Configuration
;; =============================================================================
;; This namespace defines common middleware stacks for HTTP routes.
;; Middleware are functions that wrap handlers to add cross-cutting concerns
;; like parsing parameters, managing sessions, security headers, etc.
;;
;; Middleware execution order:
;; - Applied bottom-to-top in the vector
;; - Request flows down through the list
;; - Response flows back up through the list
;;
;; Example flow:
;;   Request → wrap-cookies → wrap-params → handler
;;   Response ← wrap-cookies ← wrap-params ← handler

(ns caveman.middlewares
  (:require
   [caveman.system :as-alias system]
   [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
   [ring.middleware.content-type :refer [wrap-content-type]]
   [ring.middleware.cookies :refer [wrap-cookies]]
   [ring.middleware.default-charset :refer [wrap-default-charset]]
   [ring.middleware.flash :refer [wrap-flash]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [ring.middleware.nested-params :refer [wrap-nested-params]]
   [ring.middleware.not-modified :refer [wrap-not-modified]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.session :refer [wrap-session]]
   [ring.middleware.x-headers :as x]))

(defn standard-html-route-middleware
  "Returns a vector of middleware for standard HTML form-based routes.
   
   This middleware stack provides:
   - Security: CSRF protection, XSS prevention, clickjacking protection
   - Parsing: cookies, form params, multipart uploads
   - Session: cookie-based session storage with flash messages
   - Content negotiation: charset and content-type handling
   
   The middleware are applied in reverse order (bottom to top).
   Each middleware wraps the next one down."
  [{::system/keys [cookie-store]
    :as _system}]
  [;; Security Headers
   ;; ----------------
   ;; Prevents "media type confusion" attacks by forcing browsers to respect
   ;; the declared Content-Type header
   #(x/wrap-content-type-options % :nosniff)
   
   ;; Prevents "clickjacking" attacks by disallowing the page to be
   ;; embedded in iframes from other domains
   #(x/wrap-frame-options % :sameorigin)
   
   ;; Content Negotiation
   ;; -------------------
   ;; Returns "304 Not Modified" if the client already has the current version
   wrap-not-modified
   
   ;; Adds "; charset=utf-8" to responses if none specified
   #(wrap-default-charset % "utf-8")
   
   ;; Guesses an appropriate Content-Type if none set (based on file extension)
   wrap-content-type
   
   ;; Request Parsing
   ;; ---------------
   ;; Parses out cookies from the request Cookie header
   wrap-cookies
   
   ;; Parses urlencoded form and URL query parameters into :params
   wrap-params
   
   ;; Parses multipart/form-data requests (useful for file uploads)
   wrap-multipart-params
   
   ;; Handles "multi-value" form parameters like checkboxes
   ;; Converts foo[bar]=baz into {:foo {:bar "baz"}}
   wrap-nested-params
   
   ;; Turns any string keys in :params into keywords
   ;; "name" -> :name
   wrap-keyword-params
   
   ;; Session Management
   ;; ------------------
   ;; Handles reading and writing session data to encrypted cookies
   #(wrap-session % {:cookie-attrs {:http-only true} ; JavaScript can't access cookies
                     :store cookie-store})
   
   ;; Handles "flash" data which persists only until the immediate next request
   ;; Useful for "success" or "error" messages after redirects
   wrap-flash
   
   ;; Security: CSRF Protection
   ;; -------------------------
   ;; Ensures that POST/PUT/DELETE requests contain an anti-forgery token
   ;; Prevents Cross-Site Request Forgery attacks
   ;; Forms must include (anti-forgery-field) to generate the hidden token field
   wrap-anti-forgery])
