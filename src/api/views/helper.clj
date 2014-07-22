(ns api.views.helper
  (:require [clj-time.coerce :refer [from-sql-time]]
            [clj-time.format :refer [formatters unparse]]))

(defn view-value-helper
  "Intended for use with clojure.data.json write-str as the :value-fn"
  [key value]
  (cond (= (class value) java.sql.Timestamp) (unparse (formatters :date-time-no-ms) (from-sql-time value))
        (= (class value) java.util.UUID) (.toString value)
        (= (class value) org.postgresql.jdbc4.Jdbc4Array) (.getArray value)
        :else value))
