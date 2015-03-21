(ns api.unit.controllers.metrics
  (:require [api.controllers.metrics :refer :all]
            [midje.sweet :refer :all]
            [clj-time.core :as t]
            [clj-time.coerce :refer [to-sql-time]]))

(def row {:revenue-per-visit 5.0
          :avg-order-revenue 10.0
          :discount 2.5
          :total-revenue 100.0})

(defn get-rows
  []
  (for [day '(1 3 5 7 9)
        hour (range 0 24)]
    (assoc row :measurement-hour (to-sql-time (t/date-time 2015 2 day hour)))))

(fact "Filter rows by day"
  (count (rows-by-day (get-rows) (t/date-time 2015 2 1 0))) => 24)

(fact "Sum by day"
      (sum-column-from-rows :discount
                            (rows-by-day (get-rows) (t/date-time 2015 2 1 0))
                            (t/date-time 2015 2 1)) => 60.0)

(fact "List of days from rows"
      (list-of-days-from-rows :discount
                              (get-rows)
                              (t/date-time 2015 2 1 0)
                              (t/date-time 2015 2 10 0)) => '(60.0 0 60.0 0 60.0 0 60.0 0 60.0))

(fact "Average from rows"
      (average-from-rows :discount
                         (get-rows)
                         (t/date-time 2015 2 1 0)
                         (t/date-time 2015 2 10 0)) => 33.33)



