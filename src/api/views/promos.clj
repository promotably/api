(ns api.views.promos
  (:require [api.views.helper :refer [view-value-helper]]
            [clojure.data.json :refer [write-str]]))

(defn shape-promo
  [p]
  (write-str p
             :value-fn view-value-helper))

(defn shape-lookup
  [r]
  {:status 200 :body (pr-str (vec (map #(-> %
                                            (assoc :conditions
                                              (map (fn [c] (dissoc c :id :promo-id))
                                                   (:conditions %))
                                              :promo-id (:uuid %))
                                            (dissoc :id :uuid :site-id)) r)))})

(defn shape-new-promo
  [{:keys [success error message] :as response} accept]
  (cond
   (true? success) {:status 201}
   (and (false? success) (= error :already-exists)) {:status 409 :body message}
   :else {:status 500}))

(defn shape-validate
  [r]
  (write-str r :value-fn view-value-helper))

(defn shape-calculate
  [r]
  (write-str r :value-fn view-value-helper))
