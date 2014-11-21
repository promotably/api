(ns api.views.helper
  (:require [clj-time.coerce :refer [from-sql-time from-date]]
            [clj-time.format :refer [formatters unparse]]))

(defn view-value-helper
  "Intended for use with clojure.data.json write-str as the :value-fn"
  [value]
  (cond (= (class value) java.sql.Timestamp) (unparse (formatters :date-time-no-ms) (from-sql-time value))
        (= (class value) java.util.Date) (unparse (formatters :date-time-no-ms) (from-date value))
        (= (class value) java.util.UUID) (.toString ^java.util.UUID value)
        (= (class value) org.postgresql.jdbc4.Jdbc4Array) (.getArray ^org.postgresql.jdbc4.Jdbc4Array value)
        :else value))
