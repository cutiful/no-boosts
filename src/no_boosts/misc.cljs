(ns no-boosts.misc)

(defn escape-attr [attr]
  (clojure.string/replace attr "\"" "&quot;"))

(defn filter-toots [items]
  (filter #(= (get %1 "type") "Create") items))

(defn filter-toots-without-replies [items]
  (filter (fn [item]
            (let [obj (get item "object")]
              (and
                (contains? obj "tag")
                (empty? (filter
                          #(= (get %1 "type") "Mention")
                          (get obj "tag"))))))
            items))

(defn render-custom-emoji [text tags]
  (if (empty? tags)
    text
    (let [emoji
          (map
            #(vector (get %1 "name") (get (get %1 "icon") "url"))
            (filter #(= (get %1 "type") "Emoji") tags))]
      (if (empty? emoji)
        text
        (reduce
          #(if (nil? (nth %2 1))
             %1
             (clojure.string/replace %1 (nth %2 0)
                                     (clojure.string/join
                                       (list
                                         "<img src=\""
                                         (escape-attr (nth %2 1))
                                         "\" alt=\""
                                         (escape-attr (nth %2 0))
                                         "\" class=\"emoji\">"))))
          text emoji)))))
