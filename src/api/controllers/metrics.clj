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
         (t/interval (from-sql-time (:measurement-hour (first rows)))
                     (from-sql-time (:measurement-hour (last rows)))))))

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
  [column rows]
  (if (= (count rows) 0)
    '()
    (let [days (day-count-from-rows rows)
          first-day (from-sql-time (:measurement-hour (first rows)))]
      (for [d (range 0 days)]
       (sum-column-from-rows column rows (t/plus first-day (t/days d)))))))

(defn average-from-rows
  [column rows]
  (/ (reduce + (list-of-days-from-rows column rows))
     (float (day-count-from-rows rows))))

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

(defn get-lift
  [{:keys [params] :as request}]
  (let [{:keys [site-id start end]} params
        site-uuid (java.util.UUID/fromString site-id)
        the-site (site/find-by-site-uuid site-uuid)
        start-date (convert-date-to-site-tz
                     (f/parse custom-formatter start) the-site)
        end-date (convert-date-to-site-tz
                   (f/parse custom-formatter end) the-site)
        r (metric/site-lift-by-days site-uuid start-date end-date)
        body {:total-revenue {
                :daily {
                  :inc (list-of-days-from-rows :total-revenue-inc r)
                  :exc (list-of-days-from-rows :total-revenue-exc r)}
                :average {
                  :inc (average-from-rows :total-revenue-inc r)
                  :exc (average-from-rows :total-revenue-exc r)}}
              :avg-order-revenue {
                :daily {
                  :inc (list-of-days-from-rows :avg-order-revenue-inc r)
                  :exc (list-of-days-from-rows :avg-order-revenue-exc r)}
                :average {
                  :inc (average-from-rows :avg-order-revenue-inc r)
                  :exc (average-from-rows :avg-order-revenue-exc r)}}
              :revenue-per-visit {
                :daily {
                  :inc (list-of-days-from-rows :revenue-per-visit-inc r)
                  :exc (list-of-days-from-rows :revenue-per-visit-exc r)}
                :average {
                  :inc (average-from-rows :revenue-per-visit-inc r)
                  :exc (average-from-rows :revenue-per-visit-exc r)}}
              :order-count {
                :daily {
                  :inc (list-of-days-from-rows :order-count-inc r)
                  :exc (list-of-days-from-rows :order-count-exc r)}
                :average {
                  :inc (average-from-rows :order-count-inc r)
                  :exc (average-from-rows :order-count-exc r)}}}]
    {:status 200 :body body}))

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
        body3 (map #(-> % (assoc :avg-revenue (quot (:revenue %) (:redeemed %)))) body2)
        body4 (map #(-> % (assoc :redemption-rate (percentage (:redeemed %) (:offered %)))) body3)
        body5 (map #(-> % (assoc :conversion-rate (percentage (:orders %) (:offered %)))) body4)
        body6 (map #(-> % (assoc :avg-items-in-cart (quot (:total-items-in-cart %) (:orders %)))) body5)
        body7 (map #(-> % (assoc :avg-discount (quot (:discount %) (:orders %)))) body6)
        body8 (map #(-> % (dissoc :total-items-in-cart)) body7)]
    {:status 200 :body body8}))


