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
  (let [r (select metrics-revenue
            (aggregate (sum :number_of_orders) :number-of-orders)
            (aggregate (sum :discount) :discount)
            (aggregate (sum :total_revenue) :revenue)
            (where {:site_id site-uuid})
            (where {:measurement_hour [>= (to-sql-time start-day)]})
            (where {:measurement_hour [<= (to-sql-time end-day)]}))]
    r))

(defn site-revenue-by-days
  [site-uuid start-day end-day]
  [{}])

(defn site-promos-by-days
  [site-uuid start-day end-day]
  (let [r (select metrics-promos
            (fields :promo_id :code)
            (aggregate (sum :redemptions) :redemptions :promo_id)
            (aggregate (sum :discount) :discount :code) ;; Code can't be a field unless its included as an aggregate
            (aggregate (sum :revenue) :revenue )
            (order :revenue :ASC)
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
                  (aggregate (sum :redeemed) :redemptions)
                  (aggregate (sum :total_items_in_carts) :total-items-in-cart)
                  (aggregate (sum :revenue) :revenue)
                  (aggregate (sum :discount) :discount)
                  (order :revenue :ASC)
                  (where {:site_id site-uuid})
                  (where {:measurement_hour [>= (to-sql-time start-day)]})
                  (where {:measurement_hour [<= (to-sql-time end-day)]}))]
    r))
