(ns api.util
  (:require [clojure.string :refer [replace]]))

(defn hyphenify-key
  [k]
  (keyword (replace (name k) #"_" "-")))
