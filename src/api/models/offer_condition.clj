(ns api.models.offer-condition
  (:require [api.entities :refer :all]
            [api.lib.coercion-helper :refer [custom-matcher dash-to-underscore-keys]]
            [api.lib.schema :refer :all]
            [api.models.redemption :as redemption]
            [api.util :refer [hyphenify-key]]
            [korma.core :refer :all]
            [korma.db :refer [transaction]]
            [clojure.set :refer [rename-keys intersection]]
            [clj-time.core :refer [before? after? now]]
            [schema.core :as s]
            [schema.coerce :as sc]))

(defn- db-to-condition
  [r]
  (let [ks (keys r)
        hyphenified-params (rename-keys r (zipmap ks (map hyphenify-key ks)))]
    ((sc/coercer OfferCondition
                 (sc/first-matcher [custom-matcher
                                    sc/string-coercion-matcher]))
     hyphenified-params)))


(def DatabaseOfferCondition
  {(s/optional-key :id) s/Int
   (s/optional-key :offer-id) s/Int
   (s/required-key :uuid) s/Uuid
   (s/required-key :type) s/Str
   (s/optional-key :created-at) (s/maybe java.sql.Timestamp)
   (s/optional-key :start-date) (s/maybe java.sql.Date)
   (s/optional-key :end-date) (s/maybe java.sql.Date)
   (s/optional-key :start-time) (s/maybe java.sql.Timestamp)
   (s/optional-key :end-time) (s/maybe java.sql.Timestamp)
   (s/optional-key :minutes-since-last-offer) (s/maybe s/Int)
   (s/optional-key :minutes-on-site) (s/maybe s/Int)
   (s/optional-key :minutes-since-last-engagement) (s/maybe s/Int)
   (s/optional-key :product-views) (s/maybe s/Int)
   (s/optional-key :repeat-product-views) (s/maybe s/Int)
   (s/optional-key :items-in-cart) (s/maybe s/Int)
   (s/optional-key :shipping-zipcode) (s/maybe s/Str)
   (s/optional-key :billing-zipcode) (s/maybe s/Str)
   (s/optional-key :referer-domain) (s/maybe s/Str)
   (s/optional-key :shopper-device-type) (s/maybe (s/enum :mobile :desktop :all))
   (s/optional-key :num-orders) (s/maybe s/Int)
   (s/optional-key :period-in-days) (s/maybe s/Int)
   (s/optional-key :num-lifetime-orders) (s/maybe s/Int)
   (s/optional-key :last-order-total) (s/maybe s/Num)
   (s/optional-key :last-order-item-count) (s/maybe s/Int)
   (s/optional-key :last-order-includes-item-id) [s/Str]})

(defn- condition-to-db
  [{:keys [type] :as condition}]
  (dash-to-underscore-keys
   ((sc/coercer DatabaseOfferCondition
                (sc/first-matcher [custom-matcher
                                   sc/string-coercion-matcher]))
    (merge condition {:type (name type)}))))

(defn create-conditions!
  [c]
  (let [coerced-conditions (map condition-to-db c)]
    (db-to-condition (insert offer-conditions
                             (values (map condition-to-db c))))))

(defn delete-conditions!
  [offer-id]
  (delete offer-conditions (where {:offer_id offer-id})))

(defn update-conditions!
  [promo-id c]
  (transaction
   (delete-conditions! promo-id)
   (when-let [coerced-conditions (seq (map condition-to-db c))]
     (db-to-condition (insert offer-conditions
                              (values (map condition-to-db c)))))))
