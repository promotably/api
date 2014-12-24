(ns dev.user
  (:require [api.kinesis :refer (record-event!)]))


(defn record-product-view
  [site-id shopper-id product-id]
  (record-event!
   (:kinesis api.system/current-system)
   :trackproductview
   {:shopper-id shopper-id
    :product-id product-id
    :site-id site-id}))

(defn go
  []
  (let [sid (java.util.UUID/randomUUID)
        vid (java.util.UUID/randomUUID)
        pid "1204243"]
    (dotimes [n 5] (record-product-view sid vid pid))))

