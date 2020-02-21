(ns no-boosts.dom
  (:import goog.html.sanitizer.HtmlSanitizer)
  (:require
    [clojure.string]
    [goog.dom :as gdom]
    [enfocus.core :as ef]
    [enfocus.events :as ev])
  (:require-macros [enfocus.macros :as em]))

(def sanitizer (HtmlSanitizer.))

(defn make-target-blank [dom]
  (doseq [node (gdom/findNodes dom (fn [n] (= (.-tagName n) "A")))]
    (set! (.-rel node) "noopener")
    (set! (.-target node) "_blank"))
  dom)

(defn toot [text user date link]
  (gdom/createDom gdom/TagName.DIV "toot"
                  (gdom/createDom gdom/TagName.ASIDE "meta"
                                  (gdom/createDom gdom/TagName.SPAN "user" user)
                                  (gdom/createDom gdom/TagName.A (clj->js {:href link :target "_blank" :rel "noopener" :class "date right"})
                                                  (gdom/createDom gdom/TagName.SPAN nil date)))
                  (gdom/createDom gdom/TagName.DIV "content"
                                  (make-target-blank (gdom/safeHtmlToNode (.sanitize sanitizer text))))))

(em/defaction add-toot [toot-data]
  "#toots" (ef/append (let [obj (get toot-data "object")]
                        (toot
                          (get obj "content")
                          (get obj "attributedTo")
                          (get obj "published")
                          (get obj "url")))))

(em/defaction clear-page []
  "#toots *" (ef/remove-node))

(defn add-toots [toots]
  (doseq [toot toots]
    (add-toot toot)))
