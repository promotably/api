(ns api.views.promos
  (:require [api.views.helper :refer [view-value-helper]]
            [clojure.data.json :refer [write-str]]))

(defn shape-promo
  [p]
  (write-str p
             :value-fn view-value-helper))

(defn shape-validate
  [r]
  (write-str r
             :value-fn view-value-helper))

(defn shape-calculate
  [r]
  (write-str r :value-fn view-value-helper))
