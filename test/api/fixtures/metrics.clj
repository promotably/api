(ns api.fixtures.metrics
  (:import org.postgresql.util.PGobject)
  (:refer-clojure :exclude [set load])
  (:require
    [clojure.data.json :as json]
    [clj-time.format :as f]
    [clj-time.core :as t]
    [clj-time.coerce :as c]
    [api.fixtures.basic :as base]
    [api.controllers.metrics :refer [convert-date-to-site-tz]]
    [api.models.site :as site]
    [api.q-fix :refer :all]))


(def site-id #uuid "1ca6424e-d955-4bfe-be80-7937d5817ab0")

(defn sql-time-day-hour
  [day hour]
  (c/to-sql-time (t/date-time 2015 2 day hour)))

(def promo-id-uno #uuid "2ca6424e-d955-4bfe-be80-7937d5817ab1")
(def promo-id-duo #uuid "2ca6424e-d955-4bfe-be80-7937d5817ab2")

(def offer-id-uno #uuid "3ca6424e-d955-4bfe-be80-7937d5817ab1")
(def offer-id-duo #uuid "3ca6424e-d955-4bfe-be80-7937d5817ab2")

(def insights-base
  {:total-discount 0M
   :visits 33
   :average-session-length 388
   :abandon-count 1
   :engagements 25
   :cart-adds 6
   :total-revenue 59.02M
   :checkouts 4
   :abandon-value 40.9M
   :revenue-per-order 59.0200000000000000M
   :order-count 1
   :ssids 86
   :product-views 25
   :average-items-per-order 1.00000000000000000000M
   :total-items 1M})

(def fixture-set
  (set
    (table :metrics_revenue
           (fixture :mr-zero
                    :id 0
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 0)
                    :number_of_orders 1
                    :discount 2.50
                    :total_revenue 10.0
                    :avg_order_revenue 10.0
                    :revenue_per_visit 10.0
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mr-one
                    :id 1
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 1)
                    :number_of_orders 1
                    :discount 2.50
                    :total_revenue 10.0
                    :avg_order_revenue 10.0
                    :revenue_per_visit 10.0
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mr-two
                    :id 2
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 2)
                    :number_of_orders 1
                    :discount 2.50
                    :total_revenue 10.0
                    :avg_order_revenue 10.0
                    :revenue_per_visit 10.0
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mr-three
                    :id 3
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 24 0)
                    :number_of_orders 1
                    :discount 2.50
                    :total_revenue 10.0
                    :avg_order_revenue 10.0
                    :revenue_per_visit 10.0
                    :created_at (c/to-sql-time (t/now))))
    (table :metrics_additional_revenue
           (fixture :mar-zero
                    :id 0
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 0)
                    :number_of_orders 1
                    :discount 2.50
                    :promotably_commission 1.0
                    :revenue 10.0
                    :less_commission_and_discount 6.5
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mar-one
                    :id 1
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 1)
                    :number_of_orders 1
                    :discount 2.50
                    :promotably_commission 1.0
                    :revenue 10.0
                    :less_commission_and_discount 6.5
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mar-two
                    :id 2
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 2)
                    :number_of_orders 1
                    :discount 2.50
                    :promotably_commission 1.0
                    :revenue 10.0
                    :less_commission_and_discount 6.5
                    :created_at (c/to-sql-time (t/now))))
    (table :metrics_promos
           (fixture :mp-zero
                    :id 0
                    :site_id site-id
                    :promo_id promo-id-uno
                    :measurement_hour (sql-time-day-hour 22 0)
                    :redemptions 2
                    :discount 2.50
                    :revenue 10.0
                    :revenue_per_order 5.0
                    :code "C1"
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mp-one
                    :id 1
                    :site_id site-id
                    :promo_id promo-id-duo
                    :measurement_hour (sql-time-day-hour 22 0)
                    :redemptions 10
                    :discount 10.0
                    :revenue 100.0
                    :revenue_per_order 5.0
                    :code "C2"
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mp-two
                    :id 2
                    :site_id site-id
                    :promo_id promo-id-uno
                    :measurement_hour (sql-time-day-hour 22 1)
                    :redemptions 2
                    :discount 2.50
                    :revenue 10.0
                    :revenue_per_order 5.0
                    :code "C1"
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mp-three
                    :id 3
                    :site_id site-id
                    :promo_id promo-id-duo
                    :measurement_hour (sql-time-day-hour 22 1)
                    :redemptions 10
                    :discount 10.0
                    :revenue 100.0
                    :revenue_per_order 5.0
                    :code "C2"
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mp-four
                    :id 4
                    :site_id site-id
                    :promo_id promo-id-uno
                    :measurement_hour (sql-time-day-hour 22 2)
                    :redemptions 2
                    :discount 2.50
                    :revenue 10.0
                    :revenue_per_order 5.0
                    :code "C1"
                    :created_at (c/to-sql-time (t/now))))
    (table :metrics_rcos
           (fixture :mrc-zero
                    :id 0
                    :site_id site-id
                    :offer_id offer-id-uno
                    :measurement_hour (sql-time-day-hour 22 0)
                    :visits 100
                    :qualified 10
                    :offered 5
                    :orders 3
                    :redeemed 2
                    :redemption_rate 14.6
                    :conversion_rate 14.6
                    :total_items_in_carts 10
                    :avg_items_in_cart 1
                    :revenue 10.0
                    :avg_revenue 1.0
                    :discount 2.50
                    :avg_discount 0.50
                    :code "C1"
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mrc-one
                    :id 1
                    :site_id site-id
                    :offer_id offer-id-duo
                    :measurement_hour (sql-time-day-hour 22 0)
                    :visits 100
                    :qualified 10
                    :offered 5
                    :orders 3
                    :redeemed 2
                    :redemption_rate 14.6
                    :conversion_rate 14.6
                    :total_items_in_carts 10
                    :avg_items_in_cart 1
                    :revenue 100.0
                    :avg_revenue 1.0
                    :discount 20.50
                    :avg_discount 0.50
                    :code "C2"
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mrc-two
                    :id 2
                    :site_id site-id
                    :offer_id offer-id-uno
                    :measurement_hour (sql-time-day-hour 22 1)
                    :visits 100
                    :qualified 10
                    :offered 5
                    :orders 3
                    :redeemed 2
                    :redemption_rate 14.6
                    :conversion_rate 14.6
                    :total_items_in_carts 10
                    :avg_items_in_cart 1
                    :revenue 80.0
                    :avg_revenue 1.0
                    :discount 20.50
                    :avg_discount 0.50
                    :code "C1"
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mrc-three
                    :id 3
                    :site_id site-id
                    :offer_id offer-id-duo
                    :measurement_hour (sql-time-day-hour 22 1)
                    :visits 100
                    :qualified 10
                    :offered 5
                    :orders 3
                    :redeemed 2
                    :redemption_rate 14.6
                    :conversion_rate 14.6
                    :total_items_in_carts 10
                    :avg_items_in_cart 1
                    :revenue 100.0
                    :avg_revenue 1.0
                    :discount 20.50
                    :avg_discount 0.50
                    :code "C2"
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mrc-four
                    :id 4
                    :site_id site-id
                    :offer_id offer-id-uno
                    :measurement_hour (sql-time-day-hour 22 2)
                    :visits 100
                    :qualified 10
                    :offered 5
                    :orders 3
                    :redeemed 2
                    :redemption_rate 14.6
                    :conversion_rate 14.6
                    :total_items_in_carts 10
                    :avg_items_in_cart 1
                    :revenue 80.0
                    :avg_revenue 1.0
                    :discount 20.50
                    :avg_discount 0.50
                    :code "C1"
                    :created_at (c/to-sql-time (t/now))))
    (table :metrics_lift
           (fixture :ml-zero
                    :id 0
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 0)
                    :total_revenue_inc 1.0
                    :total_revenue_exc 1.0
                    :avg_order_revenue_inc 1.0
                    :avg_order_revenue_exc 1.0
                    :revenue_per_visit_inc 1.0
                    :revenue_per_visit_exc 1.0
                    :order_count_inc 1
                    :order_count_exc 1
                    :created_at (c/to-sql-time (t/now)))
           (fixture :ml-one
                    :id 1
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 1)
                    :total_revenue_inc 1.0
                    :total_revenue_exc 1.0
                    :avg_order_revenue_inc 1.0
                    :avg_order_revenue_exc 1.0
                    :revenue_per_visit_inc 1.0
                    :revenue_per_visit_exc 1.0
                    :order_count_inc 1
                    :order_count_exc 1
                    :created_at (c/to-sql-time (t/now)))
           (fixture :ml-two
                    :id 2
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 2)
                    :total_revenue_inc 1.0
                    :total_revenue_exc 1.0
                    :avg_order_revenue_inc 1.0
                    :avg_order_revenue_exc 1.0
                    :revenue_per_visit_inc 1.0
                    :revenue_per_visit_exc 1.0
                    :order_count_inc 1
                    :order_count_exc 1
                    :created_at (c/to-sql-time (t/now)))
           (fixture :ml-three
                    :id 3
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 3)
                    :total_revenue_inc 1.0
                    :total_revenue_exc 1.0
                    :avg_order_revenue_inc 1.0
                    :avg_order_revenue_exc 1.0
                    :revenue_per_visit_inc 1.0
                    :revenue_per_visit_exc 1.0
                    :order_count_inc 1
                    :order_count_exc 1
                    :created_at (c/to-sql-time (t/now)))
           (fixture :ml-four
                    :id 4
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 23 0)
                    :total_revenue_inc 1.0
                    :total_revenue_exc 1.0
                    :avg_order_revenue_inc 1.0
                    :avg_order_revenue_exc 1.0
                    :revenue_per_visit_inc 1.0
                    :revenue_per_visit_exc 1.0
                    :order_count_inc 1
                    :order_count_exc 1
                    :created_at (c/to-sql-time (t/now))))
    (table :metrics_insights
           (fixture :mi-zero
                    :id 0
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 0)
                    :data insights-base
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mi-one
                    :id 1
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 1)
                    :data insights-base
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mi-two
                    :id 2
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 2)
                    :data insights-base
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mi-three
                    :id 3
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 24 0)
                    :data insights-base
                    :created_at (c/to-sql-time (t/now))))))
