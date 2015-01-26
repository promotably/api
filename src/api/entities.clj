(ns api.entities
  (:require [korma.core :refer :all]))

(declare accounts users sites offers promos redemptions promo-conditions offer-conditions linked-products)

(defentity email-subscribers
  (table "email_subscribers"))

(defentity accounts
  (table "accounts")
  (has-many users)
  (has-many sites {:fk :account_id}))

(defentity users
  (table "users")
  (belongs-to accounts {:fk :account_id}))

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
