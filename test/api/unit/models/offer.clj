(ns api.unit.models.offer
  (:require [api.models.offer :refer :all]
            [clj-time.coerce :refer (to-sql-time)]
            [clj-time.core :refer (now)]
            [midje.sweet :refer :all]))


(tabular
 (fact "exists? returns expected output"
   (exists? ...site-id... ...code...) => ?result
   (provided (lookup-by {:conditions {:site_id ...site-id...
                                      :code ...code...}}) => ?lookup-result))
 ?result ?lookup-result
 false   []
 true    [{:id 1}]
 true    [{:id 1} {:id 2}])

(tabular
 (fact "by-offer-uuid returns expected output"
   (by-offer-uuid ...site-id... ...offer-id...) => ?result
   (provided (lookup-by {:conditions {:site_id ...site-id...
                                      :uuid ...offer-id...}}) => ?lookup-result))
 ?result             ?lookup-result
 nil                 []
 [{:id 1}]           [{:id 1}])


(fact "new-offer! handles when an offer already exists"
  (new-offer! {:site-id 1 :code "TEST" :reward {:promo-id ...promo-id...}})
  => (just {:success false
            :error :already-exists
            :message "A offer with code TEST already exists"})
  (provided (api.models.promo/find-by-site-and-uuid 1 ...promo-id... true) => {:id 10},
            (exists? 1 "TEST") => true))

(fact "new-offer! handles when a promo doesn't exist"
  (let [pid (java.util.UUID/randomUUID)]
    (new-offer! {:site-id ...site-id... :code ...code... :reward {:promo-id pid}})
    => (just {:success false
              :error :invalid-promo
              :message (format "Promo %s does not exist" pid)})
    (provided (api.models.promo/find-by-site-and-uuid ...site-id... pid true) => nil,
              (exists? ...site-id... ...code...) => false)))

(fact "update-offer! handles when an offer doesn't exist"
  (let [offer-uuid (java.util.UUID/randomUUID)]
    (update-offer! (str offer-uuid) {:site-id ...site-id...
                                     :reward {:promo-id ...promo-id...}})
    => (just {:success false
              :error :not-found
              :message (format "Offer id %s does not exist." offer-uuid)})
    (provided (by-offer-uuid ...site-id... offer-uuid) => nil,
              (api.models.promo/find-by-site-and-uuid ...site-id...
                                                      ...promo-id...
                                                      true) => {:id 1})))

(fact "update-offer! handles when a promo doesn't exist"
  (let [offer-uuid (java.util.UUID/randomUUID)]
    (update-offer! (str offer-uuid) {:site-id ...site-id...
                                     :reward {:promo-id 1}})
    => (just {:success false
              :error :not-found
              :message (format "Promo id %s does not exist." 1)})
    (provided (by-offer-uuid ...site-id... offer-uuid) => [{:id 2}],
              (api.models.promo/find-by-site-and-uuid ...site-id... 1 true) => nil)))

;; EXPECTED OUTPUT AFTER DB-TO-OFFER
;;{:updated-at #inst "2014-10-17T19:42:00.952016000-00:00", :name "saalsdkjfls;akjdf", :reward {:type :promo, :promo-id #uuid "2eb88f44-e506-4fac-a458-9a1af11c5ca6"}, :display-text "aklsdjfldskajf;akefj", :conditions [{:created-at #inst "2014-10-29T00:47:04.000000000-00:00", :type :dates, :start-date #inst "2014-10-27T04:00:00.000-00:00", :end-date #inst "2014-10-31T04:00:00.000-00:00"}], :active true, :id 2, :code "DUCKAH DUCKAH", :site-id 1, :uuid #uuid "ba773e86-3bf0-41e4-a49c-88f7161a83b8", :presentation {:display-text nil, :page :any, :type :lightbox}, :created-at #inst "2014-10-17T19:42:00.952016000-00:00"}

;; DB OUTPUT
;;({:presentation_type "lightbox", :display_text "aklsdjfldskajf;akefj", :presentation_display_text nil, :name "saalsdkjfls;akjdf", :offer_conditions ({:period_in_days nil, :offer_id 2, :minutes_since_last_offer nil, :product_views nil, :repeat_product_views nil, :start_time nil, :shipping_zipcode nil, :type "dates", :num_lifetime_orders nil, :last_order_includes_item_id nil, :end_time nil, :minutes_since_last_engagement nil, :end_date #inst "2014-10-31T04:00:00.000-00:00", :items_in_cart nil, :start_date #inst "2014-10-27T04:00:00.000-00:00", :id 1, :num_orders nil, :shopper_device_type nil, :minutes_on_site nil, :uuid #uuid "58d92c08-7a0f-4d59-ba99-b63430a02916", :last_order_total nil, :last_order_item_count nil, :billing_zipcode nil, :created_at #inst "2014-10-29T00:47:04.000000000-00:00", :referer_domain nil}), :updated_at #inst "2014-10-17T19:42:00.952016000-00:00", :dynamic false, :active true, :id 2, :code "DUCKAH DUCKAH", :site_id 1, :uuid #uuid "ba773e86-3bf0-41e4-a49c-88f7161a83b8", :expiry_in_minutes nil, :created_at #inst "2014-10-17T19:42:00.952016000-00:00", :promo_id 6, :presentation_page "any"})


(fact "db-to-offer returns expected output format"
  (let [offer-uuid (java.util.UUID/randomUUID)
        promo-uuid (java.util.UUID/randomUUID)
        db-result {:id 1
                   :site_id 2
                   :promo_id 42
                   :active true
                   :uuid offer-uuid
                   :name "DUCKAHN"
                   :code "DUCKAHC"
                   :presentation_type "lightbox"
                   :presentation_page "any"
                   :presentation_display_text "none"
                   :display_text "DUCKAH"
                   :created_at (to-sql-time (now))
                   :updated_at (to-sql-time (now))
                   :offer_conditions (seq [{:offer_id 1
                                            :type "dates"
                                            :start_date (to-sql-time (now))
                                            :end_date (to-sql-time (now))}])}]
    (db-to-offer db-result) => (contains {:uuid offer-uuid
                                          :reward (contains {:type :promo
                                                             :promo-id promo-uuid})
                                          :presentation (contains {:display-text "none"
                                                                   :page :any
                                                                   :type :lightbox})
                                          :conditions (just [(contains {:type :dates})])})
    (provided
      (api.models.promo/find-by-id 42) => [{:id 1 :uuid promo-uuid}])))

(fact "valid? respects active flag"
  (let [o {:active true :reward {:promo-id ...promo-id...}}
        c {}]
    (valid? c o) => true
    (provided (api.models.promo/find-by-uuid ...promo-id...) => {:something "here"}
              (api.models.promo/valid-for-offer? {:something "here"}) => true)))

(fact "valid? respects active flag false"
  (let [o {:active false :reward {:promo-id ...promo-id...}}
        c {}]
    (valid? c o) => false
    (provided (api.models.promo/find-by-uuid ...promo-id...) => {:something "here"})))

