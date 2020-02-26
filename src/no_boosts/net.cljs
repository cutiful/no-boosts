(ns no-boosts.net
  (:require
    [clojure.string]
    [ajax.core :refer [GET POST]]))

(def cors-proxy-domain "cors-anywhere.glitch.me")
(def cors-proxy-url (clojure.string/join (list "https://" cors-proxy-domain "/")))

(defn make-cors-url [url]
  (clojure.string/join (list cors-proxy-url url)))

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

(defn get-page [instance url handler]
  (GET (make-cors-url (fix-url url instance))
       {:response-format :json
        :handler handler}))

(defn load-toots [instance url number taken filter-fn handler & [s]]
  "Loads `number` new toots starting from `url`, filtering boosts and other
  unneeded stuff. `taken` is how many toots from the first page to discard.
  Returns a seq of toots, next page url and the number of toots taken from it."
  (get-page instance url (fn [page]
                  (let [next-url (get page "next")
                        activities (get page "orderedItems")
                        new-toots (filter-fn activities) ; only Create activities
                        all-toots (concat
                                    (if (nil? s) '() s) ; '() on first iteration, s after
                                    (nthrest new-toots taken))]

                    ; If we need to load new toots and it's possible,
                    (if (and (not (clojure.string/blank? next-url)) (< (count all-toots) number))
                      ; do it.
                      (load-toots instance next-url number 0 filter-fn handler all-toots)
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
