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

(defn site-revenue-by-days
  [site-uuid start-day end-day]
  (let [r (select metrics-revenue
            (aggregate (sum :number_of_orders) :number-of-orders)
            (aggregate (sum :discount) :discount)
            (aggregate (sum :promotably_commission) :promotably-commission)
            (aggregate (sum :less_commission_and_discount) :less-discounts-and-commission) ;; XXX this should be resolved
            (aggregate (sum :revenue) :revenue)
            (where {:site_id site-uuid})
            (where {:measurement_hour [>= (to-sql-time start-day)]})
            (where {:measurement_hour [<= (to-sql-time end-day)]}))]
    r))


