(ns api.lib.coercion-helper
  (:require [clojure.walk :refer [postwalk]]
            [clj-time.coerce :refer [from-sql-date from-sql-time]]
            [clj-time.format :refer [formatters parse]]
            [schema.coerce :refer [safe]]
            [schema.core :as s]))

(let [date-formatter (formatters :date-time-no-ms)]
  (defn coerce-joda-date-time
    [thing]
    (condp = (class thing)
      java.lang.String (parse date-formatter thing)
      java.sql.Date (from-sql-date thing)
      java.sql.Timestamp (from-sql-time thing))))

(let [coercions {org.joda.time.DateTime (safe #(coerce-joda-date-time %))
                 java.util.UUID (safe #(java.util.UUID/fromString %))}]
  (defn custom-matcher
    "A matcher that coerces keywords, keyword enums, s/Num and s/Int,
     and long and doubles (JVM only) from strings."
    [schema]
    (coercions schema)))

(defn underscore-to-dash-keys
  [form]
  (postwalk (fn [x] (if (keyword? x)
                      (keyword (clojure.string/replace (name x) "_" "-"))
                      x)) form))

(defn dash-to-underscore-keys
  [form]
  (postwalk (fn [x] (if (keyword? x)
                      (keyword (clojure.string/replace (name x) "-" "_"))
                      x)) form))
