(ns api.unit.models.promo
  (:require [api.models.promo :refer :all]
            [api.models.redemption :as rd]
            [clj-time.coerce :refer [to-sql-date]]
            [clj-time.core :refer [now plus minus months]]
            [midje.sweet :refer :all]))

(fact "Inactive promos are declared invalid"
  (let [the-promo {:active false}]
    (valid? the-promo anything) => (just {:valid false
                                          :messages ["That promo is currently inactive"]})))

(fact "Promos with a start date in the future are declared invalid"
  (let [the-promo {:active true
                   :conditions [{:type :dates
                                 :start-date (plus (now) (months 1))
                                 :end-date (plus (now) (months 3))}]}]
    (valid? the-promo anything) => (just {:valid false
                                          :messages ["That promo hasn't started yet"]})))

(fact "Promos that have expired are declared invalid"
  (let [the-promo {:active true
                   :conditions [{:type :dates
                                 :start-date (minus (now) (months 3))
                                 :end-date (minus (now) (months 1))}]}]
    (valid? the-promo anything) => (just {:valid false
                                          :messages ["That promo has ended"]})))

(fact "Promos with a usage-count condition are validated correctly"
  (let [the-promo {:active true
                   :conditions [{:type :usage-count
                                 :usage-count 100}]}]
    (valid? the-promo anything) => (just {:valid false
                                          :messages ["That promo is no longer available"]})
    (provided (api.models.redemption/count-by-promo-and-shopper-email anything anything) => 101)
    (valid? the-promo anything) => (just {:valid true})
    (provided (api.models.redemption/count-by-promo-and-shopper-email anything anything) => 99)))
