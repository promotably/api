(ns api.models.helper
  (:require [clojure.walk :refer [postwalk]]))

(defn- jdbc-array->seq
  [^org.postgresql.jdbc4.Jdbc4Array jdbc-array]
  (when-not (nil? jdbc-array)
    (seq (.getArray jdbc-array))))

(defn unwrap-jdbc
  "Recursively transforms all jdbc array values into seqs"
  [m]
  (let [f (fn [[k v]] (if (= org.postgresql.jdbc4.Jdbc4Array (type v))
                        [k (jdbc-array->seq v)]
                        [k v]))]
    ;; only apply to maps
    (postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

