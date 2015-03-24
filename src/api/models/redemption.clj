(ns api.models.redemption
  (:require [clojure.tools.logging :as log]
            [api.entities :refer :all]
            [clj-time.core :refer [before? after? now today-at today-at-midnight epoch
                                   plus days]]
            [clj-time.coerce :refer [from-sql-date to-sql-date to-sql-time]]
            [korma.core :refer :all]
            [schema.core :as s]
            [schema.macros :as sm]))

(defn count-in-period
  [promo-uuid & {:keys [start end]
                 :or {start (epoch)
                      end (plus (today-at-midnight) (days 1))}}]
  (try
    (-> (select promo-redemptions
                (aggregate (count :*) :c)
                (join promos (= :promos.id :promo_id))
                (where {:created_at [>= (to-sql-time start)]})
                (where {:created_at [<= (to-sql-time end)]})
                (where {:promos.uuid promo-uuid}))
        first
        :c)
    (catch java.sql.BatchUpdateException ex
      (log/error (.getNextException ex) "Exception in total-discounts"))))

(defn total-discounts
  [promo-uuid & {:keys [start end] :or {start (epoch)
                                        end (plus (today-at-midnight) (days 1))}}]
  (try
    (or (-> (select promo-redemptions
                    (aggregate (sum :promo_redemptions.discount) :total)
                    (join promos (= :promos.id :promo_id))
                    (where {:promos.uuid promo-uuid})
                    (where {:created_at [>= (to-sql-time start)]})
                    (where {:created_at [<= (to-sql-time end)]}))
            first
            :total)
        0.0)
    (catch java.sql.BatchUpdateException ex
      (log/error (.getNextException ex) "Exception in total-discounts"))))

(defn count-by-promo-and-shopper-email
  [promo-id shopper-email]
  (:shopper-redemptions
   (first (select redemptions
                  (aggregate (count :*) :shopper-redemptions)
                  (where {:promo_id promo-id
                          :shopper_email shopper-email})))))

