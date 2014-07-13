(ns ^{:doc "Unit tests for the promo calculation logic"
      :author "Thomas Steffes"}
  api.unit.models.promo-calculation
  (:require [api.models.promo :refer :all]
            [clj-time.coerce :refer [to-sql-date]]
            [clj-time.core :refer [now plus minus months]]
            [midje.sweet :refer :all]))

(tabular
 (fact "Percent product promos with overlapping
      products in the cart and usage limits are calculated properly"
   (let [the-promo {:active true
                    :type :percent-product
                    :incept-date (to-sql-date (minus (now) (months 1)))
                    :expiry-date (to-sql-date (plus (now) (months 1)))
                    :product-ids ?product-ids
                    :amount 0.10
                    :limit-usage-to-x-items ?usage}
         context {:cart-contents ?cart-contents}]
     (calculate-discount the-promo context) => (contains ?expected)))
 ?product-ids       ?usage   ?cart-contents               ?expected
 ["1234" "asdfjk"]  2        [{:product-id "1234"
                               :quantity 1
                               :line-subtotal 100.00}]    {:discount-amount 10.0
                                                           :number-discounted-items 1
                                                           :discounted-product-id "1234"}
 ["1234" "asdfjk"]  2        [{:product-id "1234"
                               :quantity 5
                               :line-subtotal 500.00}]    {:discount-amount 20.0
                                                           :number-discounted-items 2
                                                           :discounted-product-id "1234"}
 ["1234" "asdfjk"]  2        [{:product-id "1234"
                               :quantity 5
                               :line-subtotal 500.00}
                              {:product-id "asdfjk"
                               :quantity 3
                               :line-subtotal 15.00}]     {:discount-amount 1.0
                                                           :number-discounted-items 2
                                                           :discounted-product-id "asdfjk"})

(tabular
 (fact "Percent product promos with overlapping
      products in the cart and usage limits are calculated properly"
   (let [the-promo {:active true
                    :type :percent-product
                    :incept-date (to-sql-date (minus (now) (months 1)))
                    :expiry-date (to-sql-date (plus (now) (months 1)))
                    :product-ids ?product-ids
                    :amount ?amount}
         context {:cart-contents ?cart-contents}]
     (calculate-discount the-promo context) => (contains ?expected)))
 ?product-ids       ?amount  ?cart-contents               ?expected
 ;; Only one product matches, no limit-usage
 ["1234" "asdfjk"]  0.10     [{:product-id "1234"
                               :quantity 1
                               :line-subtotal 100.00}]    {:discount-amount 10.0
                                                           :number-discounted-items 1
                                                           :discounted-product-id "1234"}
 ;; Two products match, ensure it discounts the cheaper one
 ["1234" "asdfjk"]  0.10     [{:product-id "1234"
                               :quantity 1
                               :line-subtotal 100.00}
                              {:product-id "asdfjk"
                               :quantity 1
                               :line-subtotal 25.00}]      {:discount-amount 2.50
                                                            :number-discounted-items 1
                                                            :discounted-product-id "asdfjk"}
 ;; One product matches, discount entire line
 ["1234"]           0.10      [{:product-id "1234"
                                :quantity 5
                                :line-subtotal 500.00}]    {:discount-amount 50.0
                                                            :number-discounted-items 5
                                                            :discounted-product-id "1234"})

(tabular
 (fact "Percent product promos with overlapping
      product categories in the cart and usage limits are calculated properly"
   (let [the-promo {:active true
                    :type :percent-product
                    :incept-date (to-sql-date (minus (now) (months 1)))
                    :expiry-date (to-sql-date (plus (now) (months 1)))
                    :product-categories ?product-categories
                    :amount 0.10
                    :limit-usage-to-x-items ?usage}
         context {:cart-contents ?cart-contents}]
     (calculate-discount the-promo context) => (contains ?expected)))
 ?product-categories    ?usage   ?cart-contents               ?expected
 ["hammers" "skivvies"] 2        [{:product-id "1234"
                                   :product-categories ["skivvies" "boxers"]
                                   :quantity 1
                                   :line-subtotal 100.00}]    {:discount-amount 10.0
                                                               :number-discounted-items 1
                                                               :discounted-product-id "1234"})
