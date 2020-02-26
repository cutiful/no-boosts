(ns no-boosts.misc)

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
