(ns dev.user
  (:require [api.kafka :as kafka]))


(defn record-product-view
  [site-id visitor-id product-id]
  (kafka/record-event!
   :trackproductview
   {:visitor-id visitor-id
    :product-id product-id
    :site-id site-id}))

(defn go
  []
  (let [sid (java.util.UUID/randomUUID)
        vid (java.util.UUID/randomUUID)
        pid "1204243"]
    (dotimes [n 5] (record-product-view sid vid pid))))

