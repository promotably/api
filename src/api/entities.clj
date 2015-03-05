(ns api.entities
  (:require [korma.core :refer :all]))

(declare accounts users users-accounts sites offers promos redemptions promo-conditions offer-conditions linked-products)

(def tables-to-truncate
  ["email_subscribers"
   "accounts"
   "users"
   "users_accounts"
   "sites"
   "promos"
   "promo_conditions"
   "promo_redemptions"
   "linked_products"
   "events"
   "offers"
   "offer_conditions"
   "metrics_revenue"
   "metrics_additional_revenue"
   "metrics_promos"
   "metrics_rcos"])

(defentity email-subscribers
  (table "email_subscribers"))

(defentity accounts
  (table "accounts")
  (many-to-many users :users_accounts)
  (has-many sites {:fk :account_id}))

(defentity users
  (table "users")
  (many-to-many accounts :users_accounts))

(defentity users-accounts
  (table "users_accounts"))

(defentity sites
  (table "sites")
  (has-many promos {:fk :site_id})
  (has-many offers {:fk :site_id})
  (belongs-to accounts {:fk :account_id}))

(defentity promos
  (table "promos")
  (belongs-to sites {:fk :site_id})
  (has-many promo-conditions {:fk :promo_id})
  (has-many linked-products {:fk :promo_id})
  (has-many redemptions {:fk :promo_id}))

(defentity promo-redemptions
  (table "promo_redemptions"))

(defentity promo-conditions
  (table "promo_conditions")
  (belongs-to promos {:fk :promo_id}))

(defentity linked-products
  (table "linked_products")
  (belongs-to promos {:fk :promo_id}))

(defentity offers
  (table "offers")
  (belongs-to sites {:fk :site_id})
  (belongs-to promos {:fk :promo_id})
  (has-many offer-conditions {:fk :offer_id}))

(defentity offer-conditions
  (table "offer_conditions")
  (belongs-to offers {:fk :offer_id}))

(defentity events
  (table "events")
  (belongs-to sites {:fk :site_id})
  (belongs-to promos {:fk :promo_id}))

(defentity metrics-revenue
  (table "metrics_revenue"))

(defentity metrics-additional-revenue
  (table "metrics_additional_revenue"))

(defentity metrics-promos
  (table "metrics_promos"))

(defentity metrics-rcos
  (table "metrics_rcos"))