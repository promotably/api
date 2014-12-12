(ns api.redis
  (:require
   [taoensso.carmine :as car :refer (wcar)]
   [api.system :as system]))

(defmacro wcar* [& body] `(car/wcar (-> system/current-system :redis :conn) ~@body))
