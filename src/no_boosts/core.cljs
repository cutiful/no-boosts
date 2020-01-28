(ns ^:figwheel-hooks no-boosts.core
  (:import goog.html.sanitizer.HtmlSanitizer)
  (:require
    [goog.dom :as gdom]
    [ajax.core :refer [GET POST]]
    [clojure.string]
    [enfocus.core :as ef]
    [enfocus.events :as ev])
  (:require-macros [enfocus.macros :as em]))

(defonce app-state (atom {:form-url ""
                          :instance ""
                          :first ""
                          :next ""
                          :prev ""}))

(def cors-proxy-domain "cors-anywhere.glitch.me")
(def cors-proxy-url (clojure.string/join (list "https://" cors-proxy-domain "/")))

; pure
(defn make-cors-url [url]
  (clojure.string/join (list cors-proxy-url url)))

(defn return-second-arg [a b]
  b)

; dom
(def sanitizer (HtmlSanitizer.))

(defn toot [text user date link]
  (gdom/createDom gdom/TagName.DIV "toot"
                  (gdom/createDom gdom/TagName.ASIDE "meta"
                                  (gdom/createDom gdom/TagName.SPAN "username" user)
                                  (gdom/createDom gdom/TagName.A (clj->js {:href link :target "_blank" :rel "noopener" :class "date right"})
                                                  (gdom/createDom gdom/TagName.SPAN nil date)))
                  (gdom/createDom gdom/TagName.DIV "content"
                                  (gdom/safeHtmlToNode (.sanitize sanitizer text)))))

(em/defaction add-toot [toot-data]
  "#toots" (ef/append (let [obj (get toot-data "object")]
                        (toot
                          (get obj "content")
                          (get obj "attributedTo")
                          (get obj "published")
                          (get obj "url")))))

(em/defaction clear-page []
  "#toots *" (ef/remove-node))

(defn display-page [page]
  (clear-page)
  (doseq [toot-data (get page "orderedItems")]
    (add-toot toot-data)))

; ajax
(defn get-user-outbox [url handler]
  (GET url {:response-format :json
            :handler #(handler %1)}))

(defn get-user-info [url handler]
  (GET url {:response-format :json
            :headers {:accept "application/activity+json"}
            :handler #(get-user-outbox
                        (make-cors-url (get %1 "outbox"))
                        handler)}))

(defn fix-url [url instance]
  (if (clojure.string/includes? url instance)
      (url)
      (clojure.string/replace-first url cors-proxy-domain instance)))

(defn get-page-handler [page handler]
  (swap! app-state update-in [:prev] return-second-arg (fix-url (get page "prev") (:instance app-state)))
  (swap! app-state update-in [:next] return-second-arg (fix-url (get page "next") (:instance app-state)))
  (handler page))

(defn get-page [url handler]
  (GET (make-cors-url (fix-url url (:instance @app-state)))
       {:response-format :json
        :handler #(get-page-handler %1 handler)}))

(defn handle-form-url [url]
  (add-watch app-state :firstwatch #(if (not= (:first %3) (:first %4))
                                      (do
                                        (get-page (:first %4) display-page) ; FIXME
                                        (remove-watch app-state :firstwatch))))
  (swap! app-state update-in [:form-url]
         return-second-arg url)
  (swap! app-state update-in [:instance]
         return-second-arg (nth (re-find #"https://([^/]+)" url) 1))
  (get-user-info url #(swap! app-state update-in [:first] return-second-arg (get %1 "first"))))

; misc
(defn ^:after-load on-reload []
  (let [url (:form-url @app-state)]
    (if-not (clojure.string/blank? url)
      (do
        (print "loading" url)
        (handle-form-url url)))))

; main
(em/defaction setup []
  "#instance" (ev/listen :submit #(let [values (ef/from "#instance" (ef/read-form))]
                                    (.preventDefault %1) 
                                    (handle-form-url (:url values)))))

(if (= (.-readyState js/document) "loading")
  (.addEventListener js/document "DOMContentLoaded" setup)
  (setup))

; (add-watch app-state :watch #(println %4))
