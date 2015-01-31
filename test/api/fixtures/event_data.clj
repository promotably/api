(ns api.fixtures.event-data
  (:import org.postgresql.util.PGobject)
  (:refer-clojure :exclude [set load])
  (:require
   [clojure.data.json :as json]
   [clj-time.format :as f]
   [clj-time.core :as t]
   [clj-time.coerce :as c]
   [api.fixtures.common :refer [site-uuid default-account default-site]]
   [api.q-fix :refer :all]))

(def site-id (str site-uuid))
(def session-id #uuid "95f1b8b2-a77b-4fec-a2fe-334f1afa2858")
(def shopper-id #uuid "7f2fe574-974e-4f48-87fd-5ada3a4cb2bb")
(def site-shopper-id #uuid "001fd699-9d50-4b7c-af3b-3e022d379647")

(def fixture-set
  (set
   default-account
   default-site
   (table :events
          (fixture :event-1
                   :site_id site-uuid
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id shopper-id
                   :site_shopper_id site-shopper-id
                   :session_id session-id
                   :type "cartview"
                   :data {:billing-state "",
                          :shipping-country "US",
                          :event-name :cartview,
                          :shopper-id (str shopper-id),
                          :site-id site-id,
                          :shipping-city "",
                          :billing-postcode "",
                          :shipping-state "",
                          :billing-email "colin@promotably.com",
                          :cart-items
                          [{:total 120,
                            :subtotal 120,
                            :quantity 6,
                            :variation "",
                            :variation-id "",
                            :categories [""],
                            :title "Widget",
                            :sku "W100"}],
                          :billing-city "",
                          :shipping-address-1 "",
                          :shipping-email "",
                          :billing-country "US",
                          :shipping-postcode "",
                          :billing-address-1 ""})
          (fixture :event-2
                   :site_id site-uuid
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id shopper-id
                   :site_shopper_id site-shopper-id
                   :session_id session-id
                   :type "productview"
                   :data {:sku "W100",
                          :title "Widget",
                          :site-id site-id,
                          :shopper-id (str shopper-id),
                          :session-id (str session-id),
                          :modified-at "2014-11-29T15:10:57.000-00:00"
                          :user-id "1",
                          :short-description "",
                          :event-name :productview})
          (fixture :event-3
                   :site_id site-uuid
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id shopper-id
                   :site_shopper_id site-shopper-id
                   :session_id session-id
                   :type "productadd"
                   :data {:quantity 1,
                          :site-id site-id,
                          :shopper-id (str shopper-id),
                          :sku "T100",
                          :variation "",
                          :event-name :productadd,
                          :session-id (str session-id)})
          (fixture :event-4
                   :site_id site-uuid
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id shopper-id
                   :site_shopper_id site-shopper-id
                   :session_id session-id
                   :type "checkout"
                   :data {:billing-state "VA",
                          :session-id (str session-id),
                          :shopper-id (str shopper-id),
                          :site-id site-id,
                          :shipping-country "US",
                          :event-name :checkout,
                          :shipping-city "Dallas",
                          :billing-postcode "75219",
                          :shipping-state "VA",
                          :billing-email "colin@promotably.com",
                          :cart-items
                          [{:total 10,
                            :subtotal 10,
                            :quantity 1,
                            :variation "",
                            :variation-id "",
                            :categories [""],
                            :title "THNEED",
                            :sku "T100"}],
                          :billing-city "Dallas",
                          :shipping-address-1 "Suite 1450",
                          :shipping-email "",
                          :billing-country "US",
                          :shipping-postcode "75219",
                          :billing-address-1 "Suite 1450"})
          (fixture :event-5
                   :site_id site-uuid
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id shopper-id
                   :site_shopper_id site-shopper-id
                   :session_id session-id
                   :type "thankyou"
                   :data {:session-id (str session-id),
                          :event-name :thankyou,
                          :order-date "2014-12-14 09:04:27",
                          :applied-coupons [{:discount "28", :code "p4"}],
                          :shipping-methods [{:cost "0", :method "free_shipping"}],
                          :tax "0",
                          :shipping-address "Colin,Steele,Suite 1450,,Dallas,VA,75219,US",
                          :cart-items
                          [{:quantity 3,
                            :variation "",
                            :variation-id "0",
                            :categories [""],
                            :title "WIDGET",
                            :sku "W100",
                            :subtotal 60,
                            :total 60}
                           {:quantity 1,
                            :variation "",
                            :variation-id "0",
                            :categories [""],
                            :title "THNEED",
                            :sku "T100",
                            :subtotal 10,
                            :total 10}],
                          :total "112",
                          :order-id "16",
                          :billing-address "Colin,Steele,Suite 1450,,Dallas,VA,75219,US,",
                          :shopper-id (str shopper-id),
                          :site-id site-id,
                          :shipping "0",
                          :discount "28"}))))

