(ns api.unit.models.promo
  (:require [api.models.promo :refer :all]
            [api.models.redemption :as rd]
            [clj-time.coerce :refer [to-sql-date]]
            [clj-time.core :refer [now plus minus months]]
            [midje.sweet :refer :all]))

(fact "Inactive promos are declared invalid"
  (let [the-promo {:active false}]
    (valid? the-promo anything) => (just {:valid false
                                          :message "That promo is currently inactive"})))

(fact "Promos with an incept date in the future are declared invalid"
  (let [the-promo {:active true
                   :incept-date (to-sql-date (plus (now) (months 1)))}]
    (valid? the-promo anything) => (just {:valid false
                                          :message "That promo hasn't started yet"})))

(fact "Promos that have expired are declared invalid"
  (let [the-promo {:active true
                   :incept-date (to-sql-date (minus (now) (months 2)))
                   :expiry-date (to-sql-date (minus (now) (months 1)))}]
    (valid? the-promo anything) => (just {:valid false
                                          :message "That promo has expired"})))

(fact "Promos that have exceeded max usage are declared invalid"
  (let [the-promo {:active true
                   :incept-date (to-sql-date (minus (now) (months 1)))
                   :expiry-date (to-sql-date (plus (now) (months 1)))
                   :max-usage-count 100
                   :current-usage-count 101}]
    (valid? the-promo anything) => {:valid false
                                    :message "That promo is no longer available"}))

(fact "Promos where the cart contains excluded items are declared invalid"
  (let [the-promo {:active true
                   :incept-date (to-sql-date (minus (now) (months 1)))
                   :expiry-date (to-sql-date (plus (now) (months 1)))
                   :max-usage-count 100
                   :current-usage-count 1
                   :exclude-product-ids ["12345" "67890"]}
        context {:cart-items [{:product-id "12345"} {:product-id "adsf"}]}]
    (valid? the-promo context) => (just {:valid false
                                         :message "There is an excluded product in the cart"})))

(fact "Promos with an excluded product category in the cart are declared invalid"
  (let [the-promo {:active true
                   :incept-date (to-sql-date (minus (now) (months 1)))
                   :expiry-date (to-sql-date (plus (now) (months 1)))
                   :max-usage-count 100
                   :current-usage-count 1
                   :exclude-product-ids ["67890"]
                   :exclude-product-categories ["shoes"]}
        context {:cart-items [{:product-id "12345"
                               :product-categories ["shoes"]} {:product-id "adsf"}]}]
    (valid? the-promo context) => (just {:valid false
                                         :message "There is an excluded product category in the cart"})))

(fact "Promos that require products that are not in the cart are declared invalid"
  (let [the-promo {:active true
                   :product-ids ["adsf"]}
        context {:cart-items [{:product-id "jkl"}]}]
    (valid? the-promo context) => (contains {:valid false})))

(fact "Promos that require a product that the cart has are declared valid"
  (let [the-promo {:active true
                   :product-ids ["adf"]}
        context {:cart-items [{:product-id "adf"} {:product-id "klhjasideogh"}]}]
    (valid? the-promo context) => (just {:valid true})))

(fact "Promos that require a product and nothing is in the cart are declared invalid"
  (let [the-promo {:active true
                   :product-ids ["adsf"]}]
    (valid? the-promo {}) => (contains {:valid false})))

(fact "Promo validation checks for all required products"
  (let [the-promo {:active true
                   :product-ids ["adsf" "1234"]}
        context {:cart-items [{:product-id "1234"}]}]
    (valid? the-promo context) => (contains {:valid false})))

(fact "Promo validation checks individual shopper usage"
  (let [the-promo {:id ...promo-id...
                   :active true
                   :usage-limit-per-user 4}
        context {:shopper-email ...shopper-email...}]
    (valid? the-promo context) => (contains {:valid false})
    (provided (rd/count-by-promo-and-shopper-email ...promo-id...
                                                   ...shopper-email...) => 5)))

(fact "Totally valid promos are declared valid"
  (let [the-promo {:active true
                   :incept-date (to-sql-date (minus (now) (months 1)))
                   :expiry-date (to-sql-date (plus (now) (months 1)))
                   :max-usage-count 100
                   :current-usage-count 1
                   :exclude-product-ids ["12345"]
                   :exclude-product-categories ["shoes"]}
        context {:cart-items [{:product-id "67890" :product-categories ["boots"]}]}]
    (valid? the-promo context) => {:valid true}))


