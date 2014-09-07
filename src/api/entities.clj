(ns api.entities
  (:require [korma.core :refer :all]))

(declare accounts users sites promos redemptions)

(defentity email-subscribers
  (table "email_subscribers"))

(defentity accounts
  (table "accounts")
  (has-many users)
  (has-many sites))

(defentity users
  (table "users")
  (belongs-to accounts {:fk :account_id}))

(defentity sites
  (table "sites")
  (has-many promos {:fk :site_id})
  (belongs-to accounts {:fk :account_id}))

(defentity promos
  (table "promos")
  (belongs-to sites {:fk :site_id})
  (has-many conditions {:fk :promo_id})
  (has-many redemptions {:fk :promo_id}))

(defentity redemptions
  (table "redemptions")
  (belongs-to promos {:fk :promo_id}))

(defentity conditions
  (table "conditions")
  (belongs-to promos {:fk :promo_id}))

(defentity time-frame-rules
  (table "time_frame_rules")
  (belongs-to promos {:fk :promo_id}))

(defentity time-frames
  (table "time_frames")
  (belongs-to time-frame-rules))
