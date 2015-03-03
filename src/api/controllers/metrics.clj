(ns api.controllers.metrics
  (:require [api.models.metric :as metric]
            [api.lib.coercion-helper :refer [transform-map
                                             remove-nils
                                             make-trans
                                             custom-matcher
                                             underscore-to-dash-keys]]
            [schema.core :as s]
            [schema.coerce :as c]
            [clj-time.format :as f]
            [clojure.set :refer [rename-keys]]))

(def custom-formatter (f/formatter "yyyyMMdd"))

(defn round2
  "Round a double to the given precision (number of significant digits)"
  [precision d]
  (let [factor (Math/pow 10 precision)]
    (/ (Math/round (* d factor)) factor)))

(defn percentage
  [a b]
  (round2 2 (* 100 (float(/ a b)))))

(defn get-additional-revenue
  [{:keys [params] :as request}]
  (let [{:keys [site-id start end]} params
        site-uuid (java.util.UUID/fromString site-id)
        start-date (f/parse custom-formatter start)
        end-date (f/parse custom-formatter end)
        body (metric/site-revenue-by-days site-uuid start-date end-date)]
    {:status 200 :body (first body)}))

(defn get-lift [request]
  {:status 200
   :body {"conversion" {"daily" {"promotably" [3.1, 3.31, 3.42, 2.91, 3.09, 3.12, 3.23],
                                 "control" [2.95, 3.07, 3.11, 2.8, 2.85, 2.97, 3.03]},
                        "average" {"promotably" 3.17,
                                   "control" 2.97}},
          "avg-order-value" {"daily" {"promotably" [37.53, 40.01, 42.11, 45.92, 36.71, 38.05, 40.32],
                                      "control" [33.54, 37.22, 37.45, 40.11, 31.34, 32.54, 37.34]},
                             "average" {"promotably" 40.09,
                                        "control" 35.65}},
          "abandoned-carts" {"daily" {"promotably" [55.6, 54.34, 56.83, 58.1, 53.21, 50.3, 56.1],
                                      "control" [60.3, 59.21, 61.2, 63.85, 58.8, 56.2, 63.12]},
                             "average" {"promotably" 54.93,
                                        "control" 60.38}},
          "revenue-per-visit" {"daily" {"promotably" [1.03, 1.10, 1.01, 1.05, 1.11, 1.15, 1.09],
                                        "control" [0.96, 0.97, 0.91, 0.99, 1.00, 0.92, 0.98]},
                               "average" {"promotably" 1.08,
                                          "control" 0.96}}}})

(defn get-promos
  [{:keys [params] :as request}]
  (let [{:keys [site-id start end]} params
        site-uuid (java.util.UUID/fromString site-id)
        start-date (f/parse custom-formatter start)
        end-date (f/parse custom-formatter end)
        body (metric/site-promos-by-days site-uuid start-date end-date)
        body2 (map #(-> % (rename-keys {:promo_id :id})) body)
        body3 (map #(-> % (assoc :avg-revenue (quot (:revenue %) (:redemptions %)))) body2)]
    {:status 200 :body body3}))

(defn get-rco
  [{:keys [params] :as request}]
  (let [{:keys [site-id start end]} params
        site-uuid (java.util.UUID/fromString site-id)
        start-date (f/parse custom-formatter start)
        end-date (f/parse custom-formatter end)
        body (metric/site-rcos-by-days site-uuid start-date end-date)
        body2 (map #(-> % (rename-keys {:offer_id :id})) body)
        body3 (map #(-> % (assoc :avg-revenue (quot (:revenue %) (:redemptions %)))) body2)
        body4 (map #(-> % (assoc :redemption-rate (percentage (:redemptions %) (:offered %)))) body3)
        body5 (map #(-> % (assoc :conversion-rate (percentage (:orders %) (:offered %)))) body4)
        body6 (map #(-> % (assoc :avg-cart-size (quot (:total-items-in-cart %) (:orders %)))) body5)
        body7 (map #(-> % (assoc :avg-discount (quot (:discount %) (:orders %)))) body6)
        body8 (map #(-> % (dissoc :total-items-in-cart)) body7)]
    {:status 200 :body body8}))


