(ns api.unit.vbucket
  (:require [api.vbucket :refer :all]
            [midje.sweet :refer :all]))

(def uuid1 #uuid "9aca0ad1-94d4-4edf-bbbe-827c3208ba63")
(def uuid2 #uuid "4969f7bf-ea6f-4c84-9fee-212eb4453997")
(def uuid3 #uuid "f0c15a0b-f426-4a41-bf32-8dc775b46e0e")
(def uuid4 #uuid "f066ca15-a31b-4e15-935a-b759dda8f145")

(fact "For a given uuid their vbucket remains constant"
      (pick-vbucket (str uuid1)) => 80
      (pick-vbucket (str uuid2)) => 39
      (pick-vbucket (str uuid3)) => 95
      (pick-vbucket (str uuid4)) => 65)

(fact "A user is either in :test or :control based off of their #uuid or override"
      (pick-bucket (str uuid1) nil) => :test
      (pick-bucket (str uuid2) nil) => :control
      (pick-bucket (str uuid3) nil) => :test
      (pick-bucket (str uuid4) true) => :test)

