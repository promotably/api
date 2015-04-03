(ns api.models.metric
  (:require [clojure.tools.logging :as log]
            [clj-time.format]
            [clj-time.core :refer [now minus days]]
            [clj-time.coerce :refer [to-sql-date to-sql-time]]
            [clojure.set :refer [rename-keys]]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
            [api.entities :refer :all]
            [api.util :refer [hyphenify-key assoc*]]
            [api.cloudwatch :as cw]
            [api.lib.schema :refer :all]
            [korma.core :refer :all]
            [schema.core :as s]
            [schema.macros :as sm]))

(defn db-to-event
  "Translates a database result to a map"
  [r]
  (let [ks (keys r)]
    (rename-keys r (zipmap ks (map hyphenify-key ks)))))

(defn site-additional-revenue-by-days
  [site-uuid start-day end-day]
  (let [r (select metrics-additional-revenue
            (aggregate (sum :number_of_orders) :number-of-orders)
            (aggregate (sum :discount) :discount)
            (aggregate (sum :revenue) :revenue)
            (aggregate (sum :promotably_commission) :promotably-commission)
            (aggregate (sum :less_commission_and_discount) :less-commission-and-discount)
            (where {:site_id site-uuid})
            (where {:measurement_hour [>= (to-sql-time start-day)]})
            (where {:measurement_hour [<= (to-sql-time end-day)]}))]
    r))

(defn site-revenue-by-days
  [site-uuid start-day end-day]
  (let [r (select metrics-revenue
                  (fields [:total_revenue :total-revenue]
                          :discount
                          [:avg_order_revenue :avg-order-revenue]
                          [:revenue_per_visit :revenue-per-visit]
                          [:measurement_hour :measurement-hour])
                  (order :measurement_hour :ASC)
                  (where {:site_id site-uuid})
                  (where {:measurement_hour [>= (to-sql-time start-day)]})
                  (where {:measurement_hour [<= (to-sql-time end-day)]}))]
    r))

(defn site-promos-by-days
  [site-uuid start-day end-day]
  (let [r (select metrics-promos
            (fields :promo_id :code)
            (aggregate (sum :redemptions) :redemptions :promo_id)
            (aggregate (sum :discount) :discount :code) ;; Code can't be a field unless its included as an aggregate
            (aggregate (sum :revenue) :revenue )
            (order :revenue :DESC)
            (where {:site_id site-uuid})
            (where {:measurement_hour [>= (to-sql-time start-day)]})
            (where {:measurement_hour [<= (to-sql-time end-day)]}))]
    r))

(defn site-rcos-by-days
  [site-uuid start-day end-day]
  (let [r (select metrics-rcos
                  (fields :offer_id :code)
                  (aggregate (sum :visits) :visits :offer_id)
                  (aggregate (sum :qualified) :qualified :code) ;; Code can't be a field unless its included as an aggregate
                  (aggregate (sum :offered) :offered)
                  (aggregate (sum :orders) :orders)
                  (aggregate (sum :redeemed) :redeemed)
                  (aggregate (sum :total_items_in_carts) :total-items-in-cart)
                  (aggregate (sum :revenue) :revenue)
                  (aggregate (sum :discount) :discount)
                  (order :revenue :DESC)
                  (where {:site_id site-uuid})
                  (where {:measurement_hour [>= (to-sql-time start-day)]})
                  (where {:measurement_hour [<= (to-sql-time end-day)]}))]
    r))

(defn site-lift-by-days
  [site-uuid start-day end-day]
  (let [r (select metrics-lift
                  (fields [:total_revenue_inc :total-revenue-inc]
                          [:total_revenue_exc :total-revenue-exc]
                          [:avg_order_revenue_inc :avg-order-revenue-inc]
                          [:avg_order_revenue_exc :avg-order-revenue-exc]
                          [:revenue_per_visit_inc :revenue-per-visit-inc]
                          [:revenue_per_visit_exc :revenue-per-visit-exc]
                          [:order_count_inc :order-count-inc]
                          [:order_count_exc :order-count-exc]
                          [:measurement_hour :measurement-hour])
                  (order :measurement_hour :ASC)
                  (where {:site_id site-uuid})
                  (where {:measurement_hour [>= (to-sql-time start-day)]})
                  (where {:measurement_hour [<= (to-sql-time end-day)]}))]
    r))