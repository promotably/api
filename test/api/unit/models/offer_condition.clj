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
         visitor-id (java.util.UUID/randomUUID)
         context {:site-id site-id
                  :visitor-id visitor-id}]
     (validate context oc) => ?result
     (provided (api.lib.redis/get-integer anything)
               => ?actual-product-views)))
 ?oc-product-views  ?result  ?actual-product-views
 100                false    10
 100                true     100
 100                true     101)
