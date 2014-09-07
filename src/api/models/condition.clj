(ns api.models.condition
  (:require [api.entities :refer :all]
            [api.lib.schema :refer :all]
            [api.lib.coercion-helper :refer [custom-matcher]]
            [api.lib.schema :refer :all]
            [api.util :refer [hyphenify-key]]
            [korma.core :refer :all]
            [clojure.set :refer [rename-keys intersection]]
            [schema.core :as s]
            [schema.coerce :as sc]))=

(defn db-to-condition
  [r]
  (let [ks (keys r)
        hyphenified-params (rename-keys r (zipmap ks (map hyphenify-key ks)))]
    ((sc/coercer Condition
                 (sc/first-matcher [custom-matcher
                                    sc/string-coercion-matcher]))
     hyphenified-params)))

(defn condition-to-db
  [{:keys [promo-id type start-date end-date start-time end-time
           usage-count total-discounts product-ids product-categories
           not-product-ids not-product-categories combo-product-ids
           item-count item-value order-min-value]}]
  {:promo_id promo-id
   :type (name type)
   :start_date start-date
   :end_date end-date
   :start_time start-time
   :end_time end-time
   :usage_count usage-count
   :total_discounts total-discounts
   :product_ids product-ids
   :product_categories product-categories
   :not_product_ids not-product-ids
   :not_product_categories not-product-categories
   :combo_product_ids combo-product-ids
   :item_count item-count
   :item_value item-value
   :order_min_value order-min-value})

(defn create-conditions!
  [conditions]
  (db-to-condition
   (insert conditions
           (values (map condition-to-db conditions)))))
