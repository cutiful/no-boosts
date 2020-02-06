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
(def toots-per-page 20)

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

(defn add-update-toots [toots]
  (update-button-visibility)
  (add-toots toots))

(defn filter-toots [items]
  (filter #(= (get %1 "type") "Create") items))

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

(defn get-page [url handler]
  (GET (make-cors-url (fix-url url (:instance @app-state)))
       {:response-format :json
        :handler handler}))

(defn load-toots [url number taken handler & [s]]
  "Loads `number` new toots starting from `url`, filtering boosts and other
  unneeded stuff. `taken` is how many toots from the first page to discard.
  Returns a seq of toots, next page url and the number of toots taken from it."
  (get-page url (fn [page]
                  (let [next-url (get page "next")
                        activities (get page "orderedItems")
                        new-toots (filter-toots activities) ; only Create activities
                        all-toots (concat
                                    (if (nil? s) '() s) ; '() on first iteration, s after
                                    (nthrest new-toots taken))]

                    ; If we need to load new toots and it's possible,
                    (if (and (not (clojure.string/blank? next-url)) (< (count all-toots) number))
                      ; do it.
                      (load-toots next-url number 0 handler all-toots)
                      ; Otherwise, count how many toots are we gonna take from the page.
                      ; If we already took some from here, add it.
                      (let [new-taken (+ (- (min number (count all-toots)) (count s)) taken)]
                        (if (not= new-taken (count new-toots))
                          ; If we only use some toots, tell how much and return
                          ; the same URL, as it has more.
                          (handler (take number all-toots) url new-taken)
                          ; If we take a full page bc we need it all,
                          ; all-toots = number, taken is number - s = new-toots.
                          ; We then return the next URL, as current is useless.
                          ;
                          ; If we take the full page bc next is unavailable, taken
                          ; will be all-toots - s = new-toots. We return the
                          ; next URL which is nil, zero toots taken from there.
                          (handler all-toots next-url 0))))))))

(defn load-new-toots [number handler]
  "`load-toots`, but with side effects. Takes URLs from @app-state and saves new
  ones as well as `taken` there."
  (load-toots
    (:next @app-state)
    number
    (:taken @app-state)
    (fn [toots url taken]
      (swap! app-state update-in [:next] return-second-arg url)
      (swap! app-state update-in [:taken] return-second-arg taken)
      (handler toots))))

(defn handle-form-url [url]
  (swap! app-state update-in [:instance]
         return-second-arg (nth (re-find #"https://([^/]+)" url) 1))
  (swap! app-state update-in [:number]
         return-second-arg 0)
  (get-user-info url #(do
                        (clear-page)
                        (swap! app-state update-in [:first] return-second-arg (get %1 "first"))
                        (swap! app-state update-in [:next] return-second-arg (get %1 "first"))
                        (load-new-toots toots-per-page add-update-toots))))

(defn next-page [e]
  (.preventDefault e)
  (load-new-toots toots-per-page add-update-toots))

; misc
(defn ^:after-load on-reload []
  (let [url (:first @app-state)]
    (if-not (clojure.string/blank? url)
      (do
        (print "loading" url)
        (clear-page)
        (swap! app-state update-in [:taken] return-second-arg 0)
        (swap! app-state update-in [:next] return-second-arg url)
        (load-new-toots toots-per-page add-update-toots)))))

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
