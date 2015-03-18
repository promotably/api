(ns api.views.promos
  (:require [api.views.helper :refer [view-value-helper]]
            [clojure.data.json :refer [write-str]]
            [schema.core :as s]
            [schema.utils]))

(defn- prep-single-promo
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

(defn shape-promo
  [{:keys [promo error]}]
  (let [status (cond
                (and (nil? error) (not (nil? promo))) 200
                (nil? promo) 404
                :else 500)
        body (cond
              (and (nil? error) (not (nil? promo))) (prep-single-promo promo)
              (nil? promo) nil)]
    {:status status
     :body body}))

(defn shape-lookup
  [r]
  (cond
   (not (contains? r :error)) {:status 200 :body (vec (map prep-single-promo (:results r)))}
   (and (contains? r :error)
        (= (:error r) :site-not-found)) {:status 404
                                         :body "That Site Doesn't Exist"}))

(defn shape-new-promo
  [site-id {:keys [success error message promo] :as response}]
  (cond
   (true? success) {:status 201 :body (write-str (-> promo
                                                     (assoc :promo-id (:uuid promo))
                                                     (assoc :site-id site-id)
                                                     (dissoc :uuid)) :value-fn (fn [k v] (view-value-helper v)))}
   (and (false? success) (= error :already-exists)) {:status 409 :body message}
   (= (class response) schema.utils.ErrorContainer) {:status 400 :body error}
   :else {:status 500 :body error}))

(defn shape-update-promo
  [site-id {:keys [success error message promo] :as response}]
  (cond
    (true? success) {:status 200 :body (write-str (-> promo
                                                      (assoc :site-id site-id)) :value-fn (fn [k v] (view-value-helper v)))}
   (= (class response) schema.utils.ErrorContainer) {:status 400 :body error}
   :else {:status 500 :body error}))

(defn shape-validate
  [r]
  (reduce-kv (fn [m k v] (assoc m k (view-value-helper v)))
             {}
             r))

(defn shape-calculate
  [r]
  (reduce-kv (fn [m k v] (assoc m k (view-value-helper v)))
             {}
             r))
