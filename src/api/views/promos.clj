(ns api.views.promos
  (:require [clojure.data.json :refer [write-str]]
            [clj-time.coerce :refer [from-sql-time]]
            [clj-time.format :refer [formatters unparse]]))

(defn- value-helper
  [key value]
  (cond (= (class value) java.sql.Timestamp) (unparse (formatters :date-time-no-ms) (from-sql-time value))
        (= (class value) java.util.UUID) (.toString value)
        (= (class value) org.postgresql.jdbc4.Jdbc4Array) (.getArray value)
        :else value))

(defn shape-promo
  [p]
  (write-str p
             :value-fn value-helper))

(defn shape-validate
  [r]
  (write-str r
             :value-fn value-helper))

(defn shape-calculate
  [r]
  (write-str r :value-fn value-helper))
