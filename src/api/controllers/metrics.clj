(ns api.controllers.metrics
  (:require [clojure.tools.logging :as log]
            [api.models.metric :as metric]
            [api.models.site :as site]
            [api.models.promo :as promo]
            [api.models.offer :as offer]
            [api.lib.coercion-helper :refer [transform-map
                                             remove-nils
                                             make-trans
                                             custom-matcher
                                             underscore-to-dash-keys]]
            [schema.core :as s]
            [schema.coerce :as c]
            [clj-time.format :as f]
            [clj-time.core :refer [to-time-zone time-zone-for-id] :as t]
            [clj-time.coerce :refer [from-sql-time to-long from-long]]
            [clojure.set :refer [rename-keys]]
            [clj-time.core :as t]))

(def custom-formatter (f/formatter "yyyyMMdd"))

(defn percentage
  [a b]
  (metric/round2 2 (* 100 (float (/ a (float b))))))

(defn convert-date-to-site-tz
  [date site]
  (let [tz (time-zone-for-id (or (:timezone site) "UTC"))
        date-local (t/from-time-zone (f/parse custom-formatter date) tz)
        date-utc (t/to-time-zone date-local (time-zone-for-id "UTC"))]
    ;; (log/errorf "Using TZ %s %s" (:timezone site) tz)
    date-utc))

(defn get-additional-revenue
  [{:keys [params] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "metrics-additional-revenue"}}
        {:keys [site-id start end]} params
        site-uuid (java.util.UUID/fromString site-id)
        the-site (site/find-by-site-uuid site-uuid)
        start-date (convert-date-to-site-tz start the-site)
        end-date (convert-date-to-site-tz end the-site)
        body (metric/site-additional-revenue-by-days site-uuid start-date end-date)]
    (merge base-response {:status 200
                          :headers {"Cache-Control" "max-age=0, no-cache"}
                          :body (first body)})))

(defn rows-by-day
  [rows day]
  (let [i (t/interval day (t/plus day (t/days 1)))]
    (filter #(t/within? i (from-sql-time (:measurement-hour %)))
            rows)))

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
    (metric/round2 2 (/ (reduce + (list-of-days-from-rows column rows begin end))
                 (float days)))))

(defn safe-quot
  [num denom]
  (try (quot num denom)
       (catch ArithmeticException _
         0.0)))

(defn get-revenue
  [{:keys [params] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "metrics-revenue"}}
        {:keys [site-id start end]} params
        site-uuid (java.util.UUID/fromString site-id)
        the-site (site/find-by-site-uuid site-uuid)
        start-date (convert-date-to-site-tz start the-site)
        end-date (convert-date-to-site-tz end the-site)
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
    (merge base-response {:status 200
                          :headers {"Cache-Control" "max-age=0, no-cache"}
                          :body body})))

(defn get-lift
  [{:keys [params] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "metrics-lift"}}
        {:keys [site-id start end]} params
        site-uuid (java.util.UUID/fromString site-id)
        the-site (site/find-by-site-uuid site-uuid)
        start-date (convert-date-to-site-tz start the-site)
        end-date (convert-date-to-site-tz end the-site)
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
    (merge base-response {:status 200
                          :headers {"Cache-Control" "max-age=0, no-cache"}
                          :body body})))

(defn add-deleted-property
  [finder-fn metrics]
  (let [ids (map #(get % :id) metrics)
        existing (finder-fn ids)
        existing-ids (set (map #(get % :uuid) existing))]
    (map #(assoc % :deleted (not (contains? existing-ids (get % :id)))) metrics)))

(defn get-promos
  [{:keys [params] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "metrics-promos"}}
        {:keys [site-id start end]} params
        site-uuid (java.util.UUID/fromString site-id)
        the-site (site/find-by-site-uuid site-uuid)
        start-date (convert-date-to-site-tz start the-site)
        end-date (convert-date-to-site-tz end the-site)
        body (->>
               (metric/site-promos-by-days site-uuid start-date end-date)
               (map #(-> % (rename-keys {:promo_id :id})))
               (add-deleted-property promo/find-existing)
               (map #(-> % (assoc :revenue-per-order (safe-quot (:revenue %) (:redemptions %))))))]
    (merge base-response {:status 200
                          :headers {"Cache-Control" "max-age=0, no-cache"}
                          :body body})))

(defn get-rco
  [{:keys [params] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "metrics-rco"}}
        {:keys [site-id start end]} params
        site-uuid (java.util.UUID/fromString site-id)
        the-site (site/find-by-site-uuid site-uuid)
        start-date (convert-date-to-site-tz start the-site)
        end-date (convert-date-to-site-tz end the-site)
        body (->>
              (metric/site-rcos-by-days site-uuid start-date end-date)
              (map #(rename-keys % {:offer_id :id}))
              (add-deleted-property offer/find-existing)
              (map #(assoc % :avg-revenue (safe-quot (:revenue %)
                                                     (:orders %))))
              (map #(assoc % :redemption-rate (percentage (:redeemed %)
                                                          (:offered %))))
              (map #(assoc % :conversion-rate (percentage (:orders %)
                                                          (:visits %))))
              (map #(assoc % :avg-items-in-cart (safe-quot
                                                 (:total-items-in-cart %)
                                                 (:orders %))))
              (map #(assoc % :avg-discount (safe-quot (:discount %)
                                                      (:orders %))))
              (map #(dissoc % :total-items-in-cart)))]
    (merge base-response {:status 200
                          :headers {"Cache-Control" "max-age=0, no-cache"}
                          :body body})))

(defn get-insights
  [{:keys [params] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "metrics-rco"}}
        {:keys [site-id start end]} params
        site-uuid (java.util.UUID/fromString site-id)
        the-site (site/find-by-site-uuid site-uuid)
        start-date (convert-date-to-site-tz start the-site)
        end-date (convert-date-to-site-tz end the-site)
        body (metric/site-insights-by-days site-uuid start-date end-date)]
    {:status 200
     :headers {"Cache-Control" "max-age=0, no-cache"}
     :body body}))
