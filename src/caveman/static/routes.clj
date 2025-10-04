;; =============================================================================
;; Static File Routes
;; =============================================================================
;; Serves static files from the res/ directory.
;; 
;; Ring's resource-response looks for files on the classpath.
;; Since res/ is in :paths in deps.edn, files there are available.

(ns caveman.static.routes
  (:require
   [ring.util.response :as response]))

(defn favicon-ico-handler
  "Serves /favicon.ico from res/favicon.ico"
  [& _]
  (response/resource-response "/favicon.ico"))

(defn nested-file-handler
  "Serves /nested/file from res/nested/file.txt
   
   Demonstrates serving files from subdirectories."
  [& _]
  (response/resource-response "/nested/file.txt"))

(defn routes
  "Returns route definitions for static files.
   
   Note: For a production app, you'd typically:
   - Use ring.middleware.resource for /public/* pattern
   - Serve assets from CDN
   - Add cache headers"
  [_]
  [["/favicon.ico" favicon-ico-handler]
   ["/nested/file" nested-file-handler]])
