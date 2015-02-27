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


(def fixture-set
  (set
    (table :metrics_revenue
           (fixture :zero
                    :id 0
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 0)
                    :number_of_orders 1
                    :discount 2.50
                    :promotably_commission 0.50
                    :revenue 10.0
                    :less_commission_and_discount 7.0
                    :created_at (c/to-sql-time (t/now)))
           (fixture :one
                    :id 1
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 1)
                    :number_of_orders 1
                    :discount 2.50
                    :promotably_commission 0.50
                    :revenue 10.0
                    :less_commission_and_discount 7.0
                    :created_at (c/to-sql-time (t/now)))
           (fixture :two
                    :id 2
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 22 0)
                    :number_of_orders 1
                    :discount 2.50
                    :promotably_commission 0.50
                    :revenue 10.0
                    :less_commission_and_discount 7.0
                    :created_at (c/to-sql-time (t/now)))
           (fixture :three
                    :id 3
                    :site_id site-id
                    :measurement_hour (sql-time-day-hour 24 0)
                    :number_of_orders 1
                    :discount 2.50
                    :promotably_commission 0.50
                    :revenue 10.0
                    :less_commission_and_discount 7.0
                    :created_at (c/to-sql-time (t/now))))))