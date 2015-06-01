(ns api.lib.coercion-helper
  (:require [clojure.walk :refer [postwalk prewalk walk]]
            [clj-time.coerce :refer [from-sql-date from-sql-time
                                     from-date to-sql-date to-sql-time
                                     to-date]]
            [clj-time.format :refer [formatters parse]]
            [schema.coerce :refer [safe]]
            [schema.core :as s]))

(let [date-formatter (formatters :date-time-no-ms)]
  (defn coerce-joda-date-time
    [thing]
    ;; pred is the thing you're trying to coerece FROM
    (condp = (class thing)
      java.lang.String (parse date-formatter thing)
      java.sql.Date (from-sql-date thing)
      java.util.Date (from-date thing)
      java.sql.Timestamp (from-sql-time thing))))

(defn coerce-sql-date
  [thing]
  ;; pred is the thing you're trying to coerece FROM
  (condp = (class thing)
    java.util.Date (to-sql-date thing)
    java.sql.Timestamp (to-sql-date thing)
    org.joda.time.DateTime (to-sql-date thing)))

(let [date-formatter (formatters :date-time-no-ms)]
  (defn coerce-date
    [thing]
    ;; pred is the thing you're trying to coerece FROM
    (condp = (class thing)
      java.lang.String (to-date (parse date-formatter thing))
      java.sql.Date (-> thing from-sql-date to-date))))

(defn coerce-sql-timestamp
  [thing]
  ;; pred is the thing you're trying to coerece FROM
  (condp = (class thing)
    java.util.Date (to-sql-time thing)
    java.sql.Date (to-sql-time thing)
    org.joda.time.DateTime (to-sql-time thing)))

(defn coerce-to-str
  [thing]
  (condp = (class thing)
    ;; pred is the thing you're trying to coerece FROM
    java.lang.Long (format "%d" thing)))

;; Keys are the thing you're trying to coerce TO
(let [coercions {org.joda.time.DateTime (safe #(coerce-joda-date-time %))
                 java.util.UUID (safe #(java.util.UUID/fromString %))
                 java.util.Date (safe #(coerce-date %))
                 java.sql.Date (safe #(coerce-sql-date %))
                 java.lang.String (safe #(coerce-to-str %))
                 java.sql.Timestamp (safe #(coerce-sql-timestamp %))}]
  (defn custom-matcher
    "A matcher that coerces ...."
    [schema]
    (coercions schema)))

(defn underscore-to-dash-keys
  [form]
  (postwalk (fn [x] (if (keyword? x)
                      (keyword (clojure.string/replace (name x) "_" "-"))
                      x))
            form))

(defn dash-to-underscore-keys
  [form]
  (postwalk (fn [x] (if (keyword? x)
                      (keyword (clojure.string/replace (name x) "-" "_"))
                      x))
            form))

(defn transform-map
  [m pred f]
  (postwalk (fn [x]
              (if (and (vector? x) (= 2 (count x)))
                (let [[k v] x]
                  (if (pred k)
                    (f k v)
                    x))
                x))
            m))

(defn make-trans
  [pred f]
  (fn [m] (transform-map m pred f)))

(def remove-nils
  (make-trans (constantly true)
              #(if (nil? %2) nil [%1 %2])))

(defn remove-nested-nils
  [m]
  (walk (fn [x]
          (if (and (vector? x) (vector? (second x)))
            [(first x)
             (into [] (filter #(not (nil? %)) (second x)))]
            x)) identity m))
