(ns api.views.promos
  (:require [api.views.helper :refer [view-value-helper]]))

(defn shape-promo
  [p]
  (reduce-kv (fn [m k v]
               (assoc m k (view-value-helper v)))
             {} p))

(defn shape-lookup
  [r]
  {:status 200
   :body (vec (map (fn [p]
                     (let [result (-> p
                                      (#(assoc % :conditions
                                                (map (fn [c] (dissoc c :id :promo-id))
                                                     (:conditions %))
                                                :promo-id (:uuid %)))
                                      (dissoc :id :uuid :site-id))]
                       (reduce-kv (fn [m k v]
                                    (assoc m k (view-value-helper v)))
                                  {} result)))
                   r))})

(defn shape-new-promo
  [{:keys [success error message] :as response}]
  (cond
   (true? success) {:status 201}
   (and (false? success) (= error :already-exists)) {:status 409 :body message}
   :else {:status 500}))

(defn shape-validate
  [r]
  (reduce-kv (fn [m k v]
               (assoc m k (view-value-helper v)))
             {} r))

(defn shape-calculate
  [r]
  (reduce-kv (fn [m k v]
               (assoc m k (view-value-helper v)))))
