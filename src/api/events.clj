(ns api.events
  (:require [clojure.set :as set]
            [clojure.tools.logging :as log]
            [api.state :as state]
            [api.kafka :as kafka]
            [api.lib.protocols :refer (EventCache insert query)]
            [schema.core :as s]))

(def EventTypes
  (s/enum :trackProductView
          :trackProductAdd
          :trackCartView
          :trackCheckout
          :trackThankYou))

(def event-types
  [:trackProductView
   :trackProductAdd
   :trackCartView
   :trackCheckout
   :trackThankYou])

(def event-required-fields
  {:trackProductView #{:shopper-id
                       :product-id
                       :title
                       :variation}
   :trackProductAdd  #{:shopper-id
                       :product-id
                       :quantity
                       :variation}
   :trackCartView    #{:shopper-id
                       :cart-item}
   :trackCheckout    #{:shopper-id
                       :billing-address
                       :shipping-address
                       :applied-coupon
                       :cart-item}
   :trackThankYou    #{:shopper-id
                       :shopper-email
                       :billing-address
                       :billing-email
                       :shipping-address
                       :applied-coupon
                       :cart-item}})

(def event-optional-fields
  {:trackProductView [:short-description
                      :modified-at
                      :description]
   :trackProductAdd  []
   :trackCartView    []
   :trackCheckout    []
   :trackThankYou    []})

(defn- valid?
  [event-type event]
  (let [req-fields (event-type event-required-fields)
        event-fields (set (keys event))]
    (and (contains? event-types event-type)
         (set/subset? req-fields event-fields))))


(defn record-event
  [request]
  (when-let [cache (state/events-cache)]
    (insert cache (:params request)))
  (kafka/record! (merge (:params request)
                        {:shopper-id (java.util.UUID/fromString (get-in request [:params :shopper-id]))})))
