(ns api.util
  (:require [clojure.string :as str]))

(defn hyphenify-key
  [k]
  (keyword (str/replace (name k) #"_" "-")))

(defn assoc*
  "Associate key/value pairs when the value of the
  pair is not nil."
  ([map key val]
     (if (nil? val) map
         (. clojure.lang.RT (assoc map key val))))
  ([map key val & kvs]
     (let [ret (assoc* map key val)]
       (if kvs
         (recur ret (first kvs) (second kvs) (nnext kvs))
         ret))))
