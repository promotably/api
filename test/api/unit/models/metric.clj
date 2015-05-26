(ns api.unit.models.metric
  (:require [api.models.metric :refer :all]
            [clj-time.coerce :refer (to-sql-time)]
            [clj-time.core :refer (now)]
            [midje.sweet :refer :all]))


(fact "A null item in a list won't blow up sum-list"
  (sum-list [1 1 1 nil 1]) => 4)

