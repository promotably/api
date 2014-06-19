(ns api.util
  (:require [clojure.string :as str]))

(defn hyphenify-key
  [k]
  (keyword (str/replace (name k) #"_" "-")))
