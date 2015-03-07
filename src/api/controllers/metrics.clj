(ns api.controllers.metrics
  (:require [api.models.metric :as metric]
            [api.models.site :as site]
            [api.lib.coercion-helper :refer [transform-map
                                             remove-nils
                                             make-trans
                                             custom-matcher
                                             underscore-to-dash-keys]]
            [schema.core :as s]
            [schema.coerce :as c]
            [clj-time.format :as f]
            [clj-time.core :refer [to-time-zone time-zone-for-id]]
            [clj-time.coerce :refer [to-long from-long]]
            [clojure.set :refer [rename-keys]]
            [clj-time.core :as t]))

(def custom-formatter (f/formatter "yyyyMMdd"))

(defn round2
  "Round a double to the given precision (number of significant digits)"
  [precision d]
  (let [factor (Math/pow 10 precision)]
    (/ (Math/round (* d factor)) factor)))

(defn percentage
  [a b]
  (round2 2 (* 100 (float(/ a b)))))

(defn- convert-date-to-site-tz
  [the-date the-site]
  (let [tz (time-zone-for-id (:timezone the-site))
        the-date-long (to-long the-date)
        tz-offset (.getOffset tz the-date-long)]
    (from-long (+ the-date-long tz-offset))))

(defn get-additional-revenue
  [{:keys [params] :as request}]
  (let [{:keys [site-id start end]} params
        site-uuid (java.util.UUID/fromString site-id)
        the-site (site/find-by-site-uuid site-uuid)
        start-date (convert-date-to-site-tz
                    (f/parse custom-formatter start) the-site)
        end-date (convert-date-to-site-tz
                  (f/parse custom-formatter end) the-site)
        body (metric/site-additional-revenue-by-days site-uuid start-date end-date)]
    {:status 200 :body (first body)}))

(defn day-count-from-rows
  [rows]
  (+ 1 (t/in-days ; think interral is not inclusive, hence the +1
         (t/interval (:measurement-hour (first rows)) (:measurement-hour (last rows))))))

(defn rows-by-day
  [rows day]
  (filter (fn [r]
            (= (f/unparse custom-formatter (:measurement-hour r))
               (f/unparse custom-formatter day)))
          rows))

(defn sum-column-from-rows
  [column rows day]
  (reduce (fn [acc r] (+ acc (get r column))) 0 (rows-by-day rows day)))

(defn list-of-days-from-rows
  [column rows]
  (let [days (day-count-from-rows rows)
        first-day (:measurement-hour (first rows))]
    (for [d (range 0 days)]
      (sum-column-from-rows column rows (t/plus first-day (t/days d))))))

(defn average-from-rows 
  [column rows]
  (/ (reduce + (list-of-days-from-rows column rows)) (day-count-from-rows rows)))

(defn get-revenue
  [{:keys [params] :as request}]
  (let [{:keys [site-id start end]} params
        site-uuid (java.util.UUID/fromString site-id)
        the-site (site/find-by-site-uuid site-uuid)
        start-date (convert-date-to-site-tz
                     (f/parse custom-formatter start) the-site)
        end-date (convert-date-to-site-tz
                   (f/parse custom-formatter end) the-site)
        r (metric/site-revenue-by-days site-uuid start-date end-date)
        body {:total-revenue {
                :daily (list-of-days-from-rows :total-revenue r)
                :average (average-from-rows :total-revenue r)}
              :discount {
                :daily (list-of-days-from-rows :discount r)
                :average (average-from-rows :discount r)}
              :avg-order-revenue {
                :daily (list-of-days-from-rows :avg-order-revenue r)
                :average (average-from-rows :avg-order-revenue r)}
              :revenue-per-visit {
                :daily (list-of-days-from-rows :revenue-per-visit r)
                :average (average-from-rows :revenue-per-visit r)}}]
    {:status 200 :body body}))

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
        the-site (site/find-by-site-uuid site-uuid)
        start-date (convert-date-to-site-tz
                    (f/parse custom-formatter start) the-site)
        end-date (convert-date-to-site-tz
                  (f/parse custom-formatter end) the-site)
        body (metric/site-promos-by-days site-uuid start-date end-date)
        body2 (map #(-> % (rename-keys {:promo_id :id})) body)
        body3 (map #(-> % (assoc :revenue-per-order (quot (:revenue %) (:redemptions %)))) body2)]
    {:status 200 :body body3}))

(defn get-rco
  [{:keys [params] :as request}]
  (let [{:keys [site-id start end]} params
        site-uuid (java.util.UUID/fromString site-id)
        the-site (site/find-by-site-uuid site-uuid)
        start-date (convert-date-to-site-tz
                    (f/parse custom-formatter start) the-site)
        end-date (convert-date-to-site-tz
                  (f/parse custom-formatter end) the-site)
        body (metric/site-rcos-by-days site-uuid start-date end-date)
        body2 (map #(-> % (rename-keys {:offer_id :id})) body)
        body3 (map #(-> % (assoc :avg-revenue (quot (:revenue %) (:redemptions %)))) body2)
        body4 (map #(-> % (assoc :redemption-rate (percentage (:redemptions %) (:offered %)))) body3)
        body5 (map #(-> % (assoc :conversion-rate (percentage (:orders %) (:offered %)))) body4)
        body6 (map #(-> % (assoc :avg-cart-size (quot (:total-items-in-cart %) (:orders %)))) body5)
        body7 (map #(-> % (assoc :avg-discount (quot (:discount %) (:orders %)))) body6)
        body8 (map #(-> % (dissoc :total-items-in-cart)) body7)]
    {:status 200 :body body8}))


