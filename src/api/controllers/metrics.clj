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
            [clj-time.coerce :refer [from-sql-time to-long from-long]]
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
  (round2 2 (* 100 (float (/ a (float b))))))

(defn convert-date-to-site-tz
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
    {:status 200
     :headers {"Cache-Control" "max-age=0, no-cache"}
     :body (first body)}))

(defn rows-by-day
  [rows day]
  (filter (fn [r]
            (= (f/unparse custom-formatter (from-sql-time (:measurement-hour r)))
               (f/unparse custom-formatter day)))
          rows))

(defn sum-column-from-rows
  [column rows day]
  (reduce (fn [acc r] (+ acc (get r column))) 0 (rows-by-day rows day)))

(defn list-of-days-from-rows
  [column rows begin end]
  (let [days (t/in-days (t/interval begin end))]
    (for [d (range 0 days)]
      (if (= (count rows) 0)
        0
        (sum-column-from-rows column rows (t/plus begin (t/days d)))))))

(defn average-from-rows
  [column rows begin end]
  (let [days (t/in-days (t/interval begin end))]
    (round2 2 (/ (reduce + (list-of-days-from-rows column rows begin end))
                 (float days)))))

(defn safe-quot
  [num denom]
  (try (quot num denom)
       (catch ArithmeticException _
         0.0)))

(defn get-revenue
  [{:keys [params] :as request}]
  (let [{:keys [site-id start end]} params
        site-uuid (java.util.UUID/fromString site-id)
        start-date (f/parse custom-formatter start)
        end-date (f/parse custom-formatter end)
        r (metric/site-revenue-by-days site-uuid start-date end-date)
        body {:total-revenue {
                :daily (list-of-days-from-rows :total-revenue r start-date end-date)
                :average (average-from-rows :total-revenue r start-date end-date)}
              :discount {
                :daily (list-of-days-from-rows :discount r start-date end-date)
                :average (average-from-rows :discount r start-date end-date)}
              :avg-order-revenue {
                :daily (list-of-days-from-rows :avg-order-revenue r start-date end-date)
                :average (average-from-rows :avg-order-revenue r start-date end-date)}
              :revenue-per-visit {
                :daily (list-of-days-from-rows :revenue-per-visit r start-date end-date)
                :average (average-from-rows :revenue-per-visit r start-date end-date)}}]
    {:status 200
     :headers {"Cache-Control" "max-age=0, no-cache"}
     :body body}))

(defn get-lift
  [{:keys [params] :as request}]
  (let [{:keys [site-id start end]} params
        site-uuid (java.util.UUID/fromString site-id)
        start-date (f/parse custom-formatter start)
        end-date  (f/parse custom-formatter end)
        r (metric/site-lift-by-days site-uuid start-date end-date)
        body {:total-revenue {
                :daily {
                  :inc (list-of-days-from-rows :total-revenue-inc r start-date end-date)
                  :exc (list-of-days-from-rows :total-revenue-exc r start-date end-date)}
                :average {
                  :inc (average-from-rows :total-revenue-inc r start-date end-date)
                  :exc (average-from-rows :total-revenue-exc r start-date end-date)}}
              :avg-order-revenue {
                :daily {
                  :inc (list-of-days-from-rows :avg-order-revenue-inc r start-date end-date)
                  :exc (list-of-days-from-rows :avg-order-revenue-exc r start-date end-date)}
                :average {
                  :inc (average-from-rows :avg-order-revenue-inc r start-date end-date)
                  :exc (average-from-rows :avg-order-revenue-exc r start-date end-date)}}
              :revenue-per-visit {
                :daily {
                  :inc (list-of-days-from-rows :revenue-per-visit-inc r start-date end-date)
                  :exc (list-of-days-from-rows :revenue-per-visit-exc r start-date end-date)}
                :average {
                  :inc (average-from-rows :revenue-per-visit-inc r start-date end-date)
                  :exc (average-from-rows :revenue-per-visit-exc r start-date end-date)}}
              :order-count {
                :daily {
                  :inc (list-of-days-from-rows :order-count-inc r start-date end-date)
                  :exc (list-of-days-from-rows :order-count-exc r start-date end-date)}
                :average {
                  :inc (average-from-rows :order-count-inc r start-date end-date)
                  :exc (average-from-rows :order-count-exc r start-date end-date)}}}]
    {:status 200
     :headers {"Cache-Control" "max-age=0, no-cache"}
     :body body}))

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
        body3 (map #(-> % (assoc :revenue-per-order (safe-quot (:revenue %) (:redemptions %)))) body2)]
    {:status 200
     :headers {"Cache-Control" "max-age=0, no-cache"}
     :body body3}))

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
        body3 (map #(-> % (assoc :avg-revenue (safe-quot (:revenue %) (:redeemed %)))) body2)
        body4 (map #(-> % (assoc :redemption-rate (percentage (:redeemed %) (:offered %)))) body3)
        body5 (map #(-> % (assoc :conversion-rate (percentage (:orders %) (:offered %)))) body4)
        body6 (map #(-> % (assoc :avg-items-in-cart (safe-quot (:total-items-in-cart %) (:orders %)))) body5)
        body7 (map #(-> % (assoc :avg-discount (safe-quot (:discount %) (:orders %)))) body6)
        body8 (map #(-> % (dissoc :total-items-in-cart)) body7)]
    {:status 200
     :headers {"Cache-Control" "max-age=0, no-cache"}
     :body body8}))
