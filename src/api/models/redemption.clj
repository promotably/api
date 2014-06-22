(ns api.models.redemption
  (:require [api.entities :refer :all]
            [korma.core :refer :all]))

(defn count-by-promo-and-shopper-email
  [promo-id shopper-email]
  (:shopper-redemptions
   (first (select redemptions
                  (aggregate (count :*) :shopper-redemptions)
                  (where {:promo_id promo-id
                          :shopper_email shopper-email})))))
