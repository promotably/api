(ns api.entities
  (:require [korma.core :refer :all]))

(declare accounts users sites promos redemptions)

(defentity email-subscribers
  (table "public.email_subscribers"))

(defentity accounts
  (table "public.accounts")
  (has-many users)
  (has-many sites))

(defentity users
  (table "public.users")
  (belongs-to accounts {:fk :account_id}))

(defentity sites
  (table "public.sites")
  (has-many promos {:fk :site_id})
  (belongs-to accounts {:fk :account_id}))

(defentity promos
  (table "public.promos")
  (belongs-to sites {:fk :site_id})
  (has-many redemptions {:fk :promo_id}))

(defentity redemptions
  (table "public.redemptions")
  (belongs-to promos {:fk :promo_id}))

(defentity time-frame-rules
  (table "public.time_frame_rules")
  (belongs-to promos {:fk :promo_id}))

(defentity time-frames
  (table "public.time_frames")
  (belongs-to time-frame-rules))
