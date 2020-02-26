(ns ^:figwheel-hooks no-boosts.core
  (:import goog.html.sanitizer.HtmlSanitizer)
  (:require
    [clojure.string]
    [enfocus.core :as ef]
    [enfocus.events :as ev]
    [no-boosts.dom :refer [add-toots clear-page]]
    [no-boosts.net :refer [load-toots get-user-info]]
    [no-boosts.misc :refer [filter-toots filter-toots-without-replies]]))

(def toots-per-page 20)
(def default-state {:instance ""
                   :first ""
                   :next ""
                   :taken 0
                   :locked true
                   :show-replies true})

(defonce app-state (atom default-state))

(defn reset-state []
  (reset! app-state default-state))

(defn return-second-arg [a b]
  b)

(defn update-button-visibility []
  (if (clojure.string/blank? (:next @app-state))
                   (ef/at "#next" (ef/set-attr :disabled "disabled"))
                   (ef/at "#next" (ef/remove-attr :disabled))))

(defn add-update-toots [toots]
  (swap! app-state update-in [:locked] return-second-arg false)
  (update-button-visibility)
  (add-toots toots))

(defn needed-filter-func []
  (if (:show-replies @app-state)
    filter-toots
    #(filter-toots-without-replies (filter-toots %1))))

(defn load-new-toots [number filter-fn handler]
  "`load-toots`, but with side effects. Takes URLs from @app-state and saves new
  ones as well as `taken` there."
  (load-toots
    (:instance @app-state)
    (:next @app-state)
    number
    (:taken @app-state)
    filter-fn
    (fn [toots url taken]
      (swap! app-state update-in [:next] return-second-arg url)
      (swap! app-state update-in [:taken] return-second-arg taken)
      (handler toots))))

(defn handle-form-url [url show-replies]
  (reset-state)
  (swap! app-state update-in [:instance]
         return-second-arg (nth (re-find #"https://([^/]+)" url) 1))
  (swap! app-state update-in [:show-replies]
         return-second-arg show-replies)
  (get-user-info url #(do
                        (clear-page)
                        (swap! app-state update-in [:first] return-second-arg (get %1 "first"))
                        (swap! app-state update-in [:next] return-second-arg (get %1 "first"))
                        (load-new-toots toots-per-page (needed-filter-func) add-update-toots))))

(defn next-page [e]
  (.preventDefault e)
  (when-not (:locked @app-state)
    (swap! app-state update-in [:locked] return-second-arg true)
    (load-new-toots toots-per-page (needed-filter-func) add-update-toots)))

; misc
(defn ^:after-load on-reload []
  (let [url (:first @app-state)]
    (if-not (clojure.string/blank? url)
      (do
        (print "loading" url)
        (clear-page)
        (swap! app-state update-in [:taken] return-second-arg 0)
        (swap! app-state update-in [:next] return-second-arg url)
        (load-new-toots toots-per-page (needed-filter-func) add-update-toots)))))

; main
(defn setup []
  (ef/at "#instance" (ev/listen :submit #(let [values (ef/from "#instance" (ef/read-form))]
                                    (.preventDefault %1) 
                                    (handle-form-url (:url values) (:replies values)))))
  (ef/at "#next" (ev/listen :click next-page)))

; init
(defonce on-startup (do
                      (if (= (.-readyState js/document) "loading")
                        (.addEventListener js/document "DOMContentLoaded" setup)
                        (setup))
                      true))
