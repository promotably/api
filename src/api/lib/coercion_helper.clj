(ns api.lib.coercion-helper
  (:require [clj-time.format :refer [formatters parse]]
            [schema.coerce :refer [safe]]
            [schema.core :as s]))

(let [date-formatter (formatters :date-time-no-ms)
      coercions {org.joda.time.DateTime (safe #(parse date-formatter %))
                 java.util.UUID (safe #(java.util.UUID/fromString %))}]
  (defn custom-matcher
    "A matcher that coerces keywords, keyword enums, s/Num and s/Int,
     and long and doubles (JVM only) from strings."
    [schema]
    (coercions schema)))
