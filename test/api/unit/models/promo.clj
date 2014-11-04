(ns api.unit.models.promo
  (:require [api.models.promo :refer :all]
            [api.models.redemption :as rd]
            [clj-time.coerce :refer [to-sql-date to-date]]
            [clj-time.core :refer [now plus minus months]]
            [midje.sweet :refer :all]))

(fact "Inactive promos are declared invalid"
  (let [the-promo {:active false}
        output (valid? the-promo anything)]
    output => (just [anything ["That promo is currently inactive"]])))

(fact "Promos that haven't started yet are declared invalid"
  (let [the-promo {:active true
                   :conditions [{:type :dates
                                 :start-date (to-date (plus (now) (months 1)))
                                 :end-date (to-date (plus (now) (months 3)))}]}
        output (valid? the-promo {})]
    output => (contains [(contains {:errors (just ["The coupon has expired."])}) anything])))

(fact "Promos that have expired are declared invalid"
  (let [the-promo {:active true
                   :conditions [{:type :dates
                                 :start-date (to-date (minus (now) (months 3)))
                                 :end-date (to-date (minus (now) (months 1)))}]}
        output (valid? the-promo {})]
    output => (contains [(contains {:errors (just ["The coupon has expired."])}) anything])))
