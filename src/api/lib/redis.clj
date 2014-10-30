(ns api.lib.redis
  (:require [taoensso.carmine :as car :refer (wcar)]))

(def connection-options {:pool {} :spec {:host "127.0.0.1" :port 6379}})

(defn get-value
  [key]
  (wcar connection-options
        (car/get key)))

(defn get-integer
  [key]
  (when-let [val (get-value key)]
    (try
      (Integer/parseInt val)
      (catch java.lang.NumberFormatException ex
        0))))
