(ns api.unit.models.promo_condition
  (:require [api.models.promo-condition :refer :all]
            [clj-time.core :refer [now]]
            [clj-time.format :refer [parse formatters]]
            [midje.sweet :refer :all]))

(facts "times condition is applied using the site's configured timezone"
  (let [c {:type :times
           :start-time "12:00"
           :end-time "17:00"}
        context {:site {:timezone "America/New_York"}}]
    (validate context c) => (contains {:errors anything})
    ;; 2PM UTC is either going to be 9 or 10AM in Eastern depending on
    ;; DST. Either way, this SHOULD NOT validate
    (provided (now) => (parse (formatters :date-time-no-ms) "2015-03-21T14:00:00Z")))

  (let [c {:type :times
           :start-time "12:00"
           :end-time "17:00"}
        context {:site {:timezone "America/New_York"}}]
    (validate context c) => (just {:site map?})
    ;; 6PM UTC is either going to be 1 or 2 pm in Eastern depending on
    ;; DST. Either way, this SHOULD validate
    (provided (now) => (parse (formatters :date-time-no-ms) "2015-03-21T18:00:00Z"))))
