(ns no-boosts.dom
  (:import goog.html.sanitizer.HtmlSanitizer.Builder
           goog.html.SafeUrl)
  (:require
    [clojure.string]
    [goog.dom :as gdom]
    [enfocus.core :as ef]
    [enfocus.events :as ev]
    [no-boosts.misc :refer [render-custom-emoji]])
  (:require-macros [enfocus.macros :as em]))

(def sanitizer-builder (Builder.))
(.withCustomNetworkRequestUrlPolicy sanitizer-builder (fn [url options]
                                                        (if (= (get (js->clj options) "tagName") "img")
                                                          (.sanitize SafeUrl url)
                                                          nil)))

(def sanitizer (.build sanitizer-builder))

(defn make-target-blank [dom]
  (doseq [node (gdom/findNodes dom (fn [n] (= (.-tagName n) "A")))]
    (set! (.-rel node) "noopener")
    (set! (.-target node) "_blank"))
  dom)

(defn toot [text user date link summary attachment]
  (gdom/createDom gdom/TagName.DIV "toot"
                  (gdom/createDom gdom/TagName.ASIDE "meta"
                                  (gdom/createDom gdom/TagName.A (clj->js {:href link :target "_blank" :rel "noopener" :class "date right"})
                                                  (gdom/createDom gdom/TagName.SPAN nil date)))
                  (let [content
                        (gdom/createDom gdom/TagName.DIV "content"
                                        (make-target-blank (gdom/safeHtmlToNode (.sanitize sanitizer text))))]
                    (if-not (nil? summary)
                      (gdom/createDom gdom/TagName.DETAILS nil
                                      (gdom/createDom gdom/TagName.SUMMARY (clj->js {:aria-label (clojure.string/join (list "cw: " summary))}) summary)
                                      content)
                      content))
                  (when (seq attachment)
                    (gdom/createDom gdom/TagName.P "attachment"
                                    "This toot contains attachments, "
                                    (gdom/createDom gdom/TagName.A (clj->js {:href link :target "_blank" :rel "noopener"}) "open it in a new tab")
                                    " to see them."))))

(em/defaction add-toot [toot-data]
  "#toots" (ef/append (let [obj (get toot-data "object")]
                        (toot
                          (render-custom-emoji (get obj "content") (get obj "tag"))
                          (get obj "attributedTo")
                          (get obj "published")
                          (get obj "url")
                          (get obj "summary")
                          (get obj "attachment")))))

(em/defaction clear-page []
  "#toots *" (ef/remove-node))

(defn add-toots [toots]
  (doseq [toot toots]
    (add-toot toot)))
