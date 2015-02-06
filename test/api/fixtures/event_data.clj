(ns api.fixtures.event-data
  (:import org.postgresql.util.PGobject)
  (:refer-clojure :exclude [set load])
  (:require
   [clojure.data.json :as json]
   [clj-time.format :as f]
   [clj-time.core :refer [now minus days]]
   [clj-time.coerce :refer [to-sql-date]]
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
          (fixture :event-start-session
                   :site_id site-uuid
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id shopper-id
                   :site_shopper_id site-shopper-id
                   :session_id session-id
                   :created_at (to-sql-date (minus (now) (days 1)))
                   :type "session-start"
                   :data {:event-format-version "1"
                          :event-name "session-start"
                          :site-id (str site-uuid)
                          :created-at "2015-02-06T20:49:39.248Z"
                          :shopper-id (str shopper-id)
                          :site-shopper-id (str site-shopper-id)
                          :request-headers
                          {"host" "localhost:3000"
                           "user-agent" "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.111 Safari/537.36"
                           "cookie" (str "wordpress_test_cookie=WP+Cookie+check; wordpress_logged_in_37d007a56d816107ce5b52c10342db37=admin%7C1421506655%7CzFOa5B7m37LjfwAgHTUy7oCc9uUoE6L2KaNEWuahTXW%7C3eb52b214dc71e434204524820415aa02773c9c9a66bb2572a295ad884f0c7cf; wp-settings-time-1=1421333904; woocommerce_recently_viewed=8%7C10%7C15; woocommerce_items_in_cart=1; woocommerce_cart_hash=67be940bc8351bd3e4830979dd45d747; wp_woocommerce_session_37d007a56d816107ce5b52c10342db37=1%7C%7C1421506764%7C%7C1421503164%7C%7Ce42d6d8992457b88f5319798f874b174; promotably=" (str shopper-id))
                           "referer" "http://localhost:8080/"
                           "connection" "keep-alive"
                           "pragma" "no-cache"
                           "accept" "*/*"
                           "accept-language" "en-US,en;q=0.8"
                           "accept-encoding" "gzip, deflate, sdch"
                           "dnt" "1"
                           "cache-control" "no-cache"}
                          :session-id (str session-id)})
          (fixture :event-1
                   :site_id site-uuid
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id shopper-id
                   :site_shopper_id site-shopper-id
                   :session_id session-id
                   :created_at (to-sql-date (minus (now) (days 1)))
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
                   :created_at (to-sql-date (minus (now) (days 1)))
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
                   :created_at (to-sql-date (minus (now) (days 1)))
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
                   :created_at (to-sql-date (minus (now) (days 1)))
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
                   :created_at (to-sql-date (minus (now) (days 1)))
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
                          :discount "28"})
          (fixture :event-6 ;; User views thankyou page again in a new session
                   :site_id site-uuid
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id shopper-id
                   :site_shopper_id site-shopper-id
                   :session_id (java.util.UUID/randomUUID)
                   :type "thankyou"
                   :created_at (to-sql-date (minus (now) (days 0)))
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
                          :discount "28"})
          (fixture :event-pa-1
                   :site_id site-uuid
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id shopper-id
                   :site_shopper_id site-shopper-id
                   :session_id session-id
                   :type "productadd"
                   :created_at (to-sql-date (minus (now) (days 1)))
                   :data {:quantity 1,
                          :site-id site-id,
                          :shopper-id (str shopper-id),
                          :sku "T100",
                          :variation "",
                          :event-name :trackproductadd,
                          :session-id (str session-id)})
          (fixture :event-pa-2
                   :site_id site-uuid
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id shopper-id
                   :site_shopper_id site-shopper-id
                   :session_id session-id
                   :type "productadd"
                   :created_at (to-sql-date (minus (now) (days 2)))
                   :data {:quantity 1,
                          :site-id site-id,
                          :shopper-id (str shopper-id),
                          :sku "T100",
                          :variation "",
                          :event-name :trackproductadd,
                          :session-id (str session-id)})
          (fixture :event-pa-3
                   :site_id site-uuid
                   :event_id (java.util.UUID/randomUUID)
                   :shopper_id shopper-id
                   :site_shopper_id site-shopper-id
                   :session_id session-id
                   :type "productadd"
                   :created_at (to-sql-date (minus (now) (days 3)))
                   :data {:quantity 1,
                          :site-id site-id,
                          :shopper-id (str shopper-id),
                          :sku "T100",
                          :variation "",
                          :event-name :trackproductadd,
                          :session-id (str session-id)}))))

