(ns ^:figwheel-hooks no-boosts.core
  (:import goog.html.sanitizer.HtmlSanitizer)
  (:require
    [goog.dom :as gdom]
    [ajax.core :refer [GET POST]]
    [clojure.string]
    [enfocus.core :as ef]
    [enfocus.events :as ev])
  (:require-macros [enfocus.macros :as em]))

(defonce app-state (atom {:instance ""
                          :first ""
                          :next ""
                          :taken 0}))

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
                                  (gdom/createDom gdom/TagName.SPAN "user" user)
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

(defn update-button-visibility []
  (if (clojure.string/blank? (:next @app-state))
                   (ef/at "#next" (ef/set-attr :disabled "disabled"))
                   (ef/at "#next" (ef/remove-attr :disabled))))

(defn add-toots [toots]
  (doseq [toot toots]
    (add-toot toot)))

(defn filter-toots [items]
  (filter #(= (get %1 "type") "Create") items))

(defn add-page [page]
  (update-button-visibility)
  (add-toots (filter-toots (get page "orderedItems"))))

; ajax
(defn get-user-outbox [url handler]
  (GET (make-cors-url url) {:response-format :json
            :handler #(handler %1)}))

(defn get-user-info [url handler]
  (GET (make-cors-url url) {:response-format :json
            :headers {:accept "application/activity+json"}
            :handler #(get-user-outbox
                        (get %1 "outbox")
                        handler)}))

(defn fix-url [url instance]
  (if (clojure.string/includes? url instance)
      url
      (clojure.string/replace-first url cors-proxy-domain instance)))

(defn update-state-page [page]
  (if-not (clojure.string/blank? (get page "next"))
    (swap! app-state update-in [:next] return-second-arg (fix-url (get page "next") (:instance @app-state)))
    (swap! app-state update-in [:next] return-second-arg "")))

(defn get-page [url handler]
  (GET (make-cors-url (fix-url url (:instance @app-state)))
       {:response-format :json
        :handler handler}))

(defn handle-form-url [url]
  (swap! app-state update-in [:instance]
         return-second-arg (nth (re-find #"https://([^/]+)" url) 1))
  (swap! app-state update-in [:number]
         return-second-arg 0)
  (get-user-info url #(do
                        (clear-page)
                        (swap! app-state update-in [:first] return-second-arg (get %1 "first"))
                        (get-page (get %1 "first") (fn [page]
                                                     (update-state-page page)
                                                     (add-page page))))))

(defn load-new-toots [url number taken handler & [s]]
  "Returns a seq of toots, next page url and the number of toots taken from it."
  (get-page url (fn [page]
                  (let [next-url (get page "next")
                        activities (get page "orderedItems")
                        new-toots (filter-toots activities) ; only Create activities
                        all-toots (concat
                                    (if (nil? s) '() s) ; '() on first iteration, s after
                                    (nthrest new-toots taken))]

                    (if-not (clojure.string/blank? next-url)
                      (if (< (count all-toots) number)
                        (load-new-toots next-url number 0 handler all-toots)
                        (let [new-taken (- number (count s))]
                          (if (> new-taken 0)
                            (handler (take number all-toots) url new-taken)
                            (handler (take number all-toots) next-url 0))))
                      (if (> (count all-toots) number)
                        (handler (take number all-toots) url (- number (count s)))
                        (handler all-toots "" 0)))))))

(defn next-page [e]
  (.preventDefault e)
  (get-page (:next @app-state) (fn [page]
                                 (update-state-page page)
                                 (add-page page))))

; misc
(defn ^:after-load on-reload []
  (let [url (:first @app-state)]
    (if-not (clojure.string/blank? url)
      (do
        (print "loading" url)
        (clear-page)
        (get-page url (fn [page]
                        (update-state-page page)
                        (add-page page)))))))

; main
(defn setup []
  (ef/at "#instance" (ev/listen :submit #(let [values (ef/from "#instance" (ef/read-form))]
                                    (.preventDefault %1) 
                                    (handle-form-url (:url values)))))
  (ef/at "#next" (ev/listen :click next-page)))

; init
(defonce on-startup (do
                      (if (= (.-readyState js/document) "loading")
                        (.addEventListener js/document "DOMContentLoaded" setup)
                        (setup))
                      true))
