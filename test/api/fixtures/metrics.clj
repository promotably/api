(ns api.fixtures.metrics
  (:import org.postgresql.util.PGobject)
  (:refer-clojure :exclude [set load])
  (:require
    [clojure.data.json :as json]
    [clj-time.format :as f]
    [clj-time.core :as t]
    [clj-time.coerce :as c]
    [api.fixtures.basic :as base]
    [api.q-fix :refer :all]))

(defn sql-time-day-hour
  [day hour]
  (c/to-sql-time (t/date-time 2015 2 day hour)))

(def site-id #uuid "1ca6424e-d955-4bfe-be80-7937d5817ab0")
(def site-id2 #uuid "2ca6424e-d955-4bfe-be80-7937d5817ab0")

(def promo-id-uno #uuid "2ca6424e-d955-4bfe-be80-7937d5817ab1")
(def promo-id-duo #uuid "2ca6424e-d955-4bfe-be80-7937d5817ab2")


(def fixture-set
  (set
    (table :metrics_revenue
           (fixture :mr-zero
                    :id 0
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 0)
                    :number_of_orders 1
                    :discount 2.50
                    :promotably_commission 0.50
                    :revenue 10.0
                    :less_commission_and_discount 7.0
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mr-one
                    :id 1
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 1)
                    :number_of_orders 1
                    :discount 2.50
                    :promotably_commission 0.50
                    :revenue 10.0
                    :less_commission_and_discount 7.0
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mr-two
                    :id 2
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 2)
                    :number_of_orders 1
                    :discount 2.50
                    :promotably_commission 0.50
                    :revenue 10.0
                    :less_commission_and_discount 7.0
                    :created_at (c/to-sql-time (t/now)))
           (fixture :mr-three
                    :id 3
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 24 0)
                    :number_of_orders 1
                    :discount 2.50
                    :promotably_commission 0.50
                    :revenue 10.0
                    :less_commission_and_discount 7.0
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
                    :code "C1"
                    :created_at (c/to-sql-time (t/now))))))