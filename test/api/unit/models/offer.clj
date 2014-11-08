(ns api.unit.models.offer
  (:require [api.models.offer :refer :all]
            [midje.sweet :refer :all]))


(tabular
 (fact "exists? returns expected output"
   (exists? ...site-id... ...code...) => ?result
   (provided (lookup-by {:site_id ...site-id...
                         :code ...code...}) => ?lookup-result))
 ?result ?lookup-result
 false   []
 true    [{:id 1}]
 true    [{:id 1} {:id 2}])

(tabular
 (fact "by-offer-uuid returns expected output"
   (by-offer-uuid ...site-id... ...offer-id...) => ?result
   (provided (lookup-by {:site_id ...site-id...
                         :uuid ...offer-id...}) => ?lookup-result))
 ?result             ?lookup-result
 nil                 []
 [{:id 1}]           [{:id 1}])


(fact "new-offer! handles when an offer already exists"
  (new-offer! {:site-id 1 :code "TEST" :reward {:promo-id ...promo-id...}})
  => (just {:success false
            :error :already-exists
            :message "A offer with code TEST already exists"})
  (provided (api.models.promo/find-by-site-and-uuid 1 ...promo-id...) => [{:id 10}],
            (exists? 1 "TEST") => true))

(fact "new-offer! handles when a promo doesn't exist"
  (let [pid (java.util.UUID/randomUUID)]
    (new-offer! {:site-id ...site-id... :code ...code... :reward {:promo-id pid}})
    => (just {:success false
              :error :invalid-promo
              :message (format "Promo %s does not exist" pid)})
    (provided (api.models.promo/find-by-site-and-uuid ...site-id... pid) => [],
              (exists? ...site-id... ...code...) => false)))

(fact "update-offer! handles when an offer doesn't exist"
  (let [offer-uuid (java.util.UUID/randomUUID)]
    (update-offer! (str offer-uuid) {:site-id ...site-id...
                                     :reward {:promo-id ...promo-id...}})
    => (just {:success false
              :error :not-found
              :message (format "Offer id %s does not exist." offer-uuid)})
    (provided (by-offer-uuid ...site-id... offer-uuid) => [],
              (api.models.promo/find-by-site-and-uuid ...site-id...
                                                      ...promo-id...) => [{:id 1}])))

(fact "update-offer! handles when a promo doesn't exist"
  (let [offer-uuid (java.util.UUID/randomUUID)]
    (update-offer! (str offer-uuid) {:site-id ...site-id...
                                     :reward {:promo-id 1}})
    => (just {:success false
              :error :not-found
              :message (format "Promo id %s does not exist." 1)})
    (provided (by-offer-uuid ...site-id... offer-uuid) => [{:id 2}],
              (api.models.promo/find-by-site-and-uuid ...site-id... 1) => [])))
