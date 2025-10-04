;; =============================================================================
;; HTML Page Layout
;; =============================================================================
;; Shared HTML layout components using Hiccup.
;; Hiccup uses Clojure data structures to represent HTML:
;; [:tag {:attr "value"} children...]

(ns caveman.page-html.core)

(defn view
  "Main page layout template.
   
   Accepts keyword arguments:
   - :body - Hiccup data structure for page content
   - :title - Page title (defaults to 'The Website')
   
   Returns Hiccup data structure representing full HTML document.
   
   Usage:
   (hiccup/html
     (page-html/view
       :title \"My Page\"
       :body [:div [:h1 \"Hello!\"]]))
   
   This is a simple layout. In a real app, we would have:
   - CSS links
   - JavaScript
   - Meta tags (description, og:image, etc.)
   - Navigation
   - Footer"
  [& {:keys [body title]
      :or {title "The Website"}}]
  [:html
   [:head {}
    [:title title]
    ;; Mobile responsive viewport
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1.0"}]]
   [:body
    body]])
