(ns api.views.promos
  (:require [api.views.helper :refer [view-value-helper]]))

(defn shape-promo
  [p]
  (let [result (-> (assoc p
                     :conditions (map (fn [c]
                                        (dissoc c :id :promo-id))
                                      (:conditions p))
                     :promo-id (:uuid p))
                   (dissoc :id :uuid :site-id))]
    (reduce-kv (fn [m k v]
                 (assoc m k (view-value-helper v)))
               {}
               result)))

(defn shape-lookup
  [r]
  {:status 200
   :body (vec (map shape-promo r))})

(defn shape-new-promo
  [{:keys [success error message] :as response}]
  (cond
   (true? success) {:status 201}
   (and (false? success) (= error :already-exists)) {:status 409 :body message}
   (= (class response) schema.utils.ErrorContainer) {:status 400 :body error}
   :else {:status 500 :body error}))

(defn shape-update-promo
  [{:keys [success error message] :as response}]
  (cond
   (true? success) {:status 204}
   (= (class response) schema.utils.ErrorContainer) {:status 400 :body error}
   :else {:status 500 :body error}))

(defn shape-validate
  [r]
  (reduce-kv (fn [m k v]
               (assoc m k (view-value-helper v)))
             {} r))

(defn shape-calculate
  [r]
  (reduce-kv (fn [m k v]
               (assoc m k (view-value-helper v)))))
