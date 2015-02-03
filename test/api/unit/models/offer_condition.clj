(ns api.unit.models.offer-condition
  (:require [api.models.offer-condition :refer :all]
            [clj-time.coerce :refer (to-sql-date)]
            [clj-time.core :refer (now minus plus months)]
            [midje.sweet :refer :all]))

(tabular
 (fact "validation for dates condition works properly"
   (let [oc {:type :dates}
         context {}]
     (validate context (merge oc {:start-date (to-sql-date ?start-date)
                                  :end-date (to-sql-date ?end-date)})) => ?result))
 ?start-date                ?end-date                 ?result
 (plus (now) (months 1))    (plus (now) (months 2))   false
 (minus (now) (months 1))   (plus (now) (months 1))   true
 (minus (now) (months 2))   (minus (now) (months 1))  false)


(tabular
 (fact "validation for product-views condition works properly"
   (let [oc {:type :product-views
             :product-views ?oc-product-views}
         site-id (java.util.UUID/randomUUID)
         shopper-id (java.util.UUID/randomUUID)
         context {:site-id site-id
                  :shopper-id shopper-id}]
     (validate context oc) => ?result
     (provided (api.models.event/count-shopper-events-by-days anything anything anything)
               => ?actual-product-views)))
 ?oc-product-views  ?result  ?actual-product-views
 100                false    10
 100                true     100
 100                true     101)

(tabular
 (fact "validation for repeat-product-views condition works properly"
   (let [oc {:type :repeat-product-views
             :repeat-product-views ?rpv
             :period-in-days ?pid}
         sid (java.util.UUID/randomUUID)
         ssid (java.util.UUID/randomUUID)
         context {:site-id sid
                  :site-shopper-id ssid}]
     (validate context oc) => ?result
     (provided (api.models.event/shopper-events anything anything anything anything)
               => ?events)))
 ?rpv ?pid ?result ?events
 1    1    true    [{:data {:sku "1234"}}]
 1    1    false   []
 2    1    false   [{:data {:sku "1234"}} {:data {:sku "5678"}}])


(tabular
 (fact "validation for num-lifetime-orders condition works properly"
   (let [oc {:type :num-lifetime-orders
             :num-lifetime-orders ?oc-num-lifetime-orders}
         site-id (java.util.UUID/randomUUID)
         site-shopper-id (java.util.UUID/randomUUID)
         context {:site-id site-id
                  :site-shopper-id site-shopper-id}]
     (validate context oc) => ?result
     (provided (api.models.event/orders-since anything anything anything)
               => ?actual-lifetime-orders)))
 ?oc-num-lifetime-orders  ?result  ?actual-lifetime-orders
 100                      false    10
 100                      true     100
 100                      true     101)


