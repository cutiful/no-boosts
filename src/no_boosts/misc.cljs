(ns no-boosts.misc)

(defn filter-toots [items]
  (filter #(= (get %1 "type") "Create") items))
