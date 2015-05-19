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

(defn safe-quot
  [num denom]
  (try (quot num denom)
       (catch ArithmeticException _
         0.0)))

(defn round2
  "Round a double to the given precision (number of significant digits)"
  [precision d]
  (let [factor (Math/pow 10 precision)]
    (/ (Math/round (* d factor)) factor)))

(defn get-all
  "For a list of maps, return all values for given key in a list"
  [key list-of-maps]
  (map #(get % key) list-of-maps))

(defn average-list
  "Average a list of numbers"
  [values]
  (round2 2 (safe-quot (apply + values) (count values))))

(defn sum-list
  "Sum a list of numbers"
  [values]
  (apply + values))

(defn insights-json-aggregate
  "The data in metrics_insights is a JSON structure. Some elements can simply be
   summed and others averaged. This is way too verbose so maybe a convention is in order."
  [data]
  {:total-discount (round2 2 (sum-list (get-all :total-discount data)))
   :visits (sum-list (get-all :visits data))
   :average-session-length (average-list (get-all :average-session-length data))
   :abandon-count (sum-list (get-all :abandon-count data))
   :engagements (sum-list (get-all :engagements data))
   :cart-adds (sum-list (get-all :cart-adds data))
   :total-revenue (round2 2 (sum-list (get-all :total-revenue data)))
   :checkouts (sum-list (get-all :checkouts data))
   :abandon-value (sum-list (get-all :abandon-value data))
   :revenue-per-order (average-list (get-all :revenue-per-order data))
   :order-count (sum-list (get-all :order-count data))
   :ssids (sum-list (get-all :ssids data))
   :product-views (sum-list (get-all :product-views data))
   :average-items-per-order (average-list (get-all :average-items-per-order data))
   :total-items (sum-list (get-all :total-items data))})

(defn site-insights-by-days
  [site-uuid start-day end-day]
  (let [r (select metrics-insights
                  (fields [:data]
                          [:measurement_hour :measurement-hour])
                  (order :measurement_hour :ASC)
                  (where {:site_id site-uuid})
                  (where {:measurement_hour [>= (to-sql-time start-day)]})
                  (where {:measurement_hour [<= (to-sql-time end-day)]}))
        i (map #(get % :data) r)]
    (insights-json-aggregate i)))

