(ns ^:figwheel-hooks no-boosts.core
  (:require
   [goog.dom :as gdom]
   [ajax.core :refer [GET POST]]
   [clojure.string]))

; defonce
(def app-state (atom {:instance ""
                      :first ""
                      :next ""
                      :prev ""}))

(def cors-proxy-domain "cors-anywhere.glitch.me")
(def cors-proxy-url (clojure.string/join (list "https://" cors-proxy-domain "/")))

(defn make-cors-url [url]
  (clojure.string/join (list cors-proxy-url url)))

(defn return-second-arg [a b]
  b)

(defn get-app-element []
  (gdom/getElement "app"))

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
                                        (get-page (:first %4) println) ; FIXME
                                        (remove-watch app-state :firstwatch))))
  (swap! app-state update-in [:instance]
         return-second-arg (nth (re-find #"https://([^/]+)" url) 1))
  (get-user-info url #(swap! app-state update-in [:first] return-second-arg (get %1 "first"))))

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)

(handle-form-url "https://mastodon.social/@admin")
(add-watch app-state :watch #(println %4))
