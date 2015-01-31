(ns api.unit.models.event
  (:require [api.models.event :refer :all]
            [clj-time.coerce :refer (to-sql-time)]
            [clj-time.core :refer (now)]
            [midje.sweet :refer :all]))

(fact "db-to-event returns expected output format"
      (let [uuid (java.util.UUID/randomUUID)
            db-result {:id 1
                       :event_id uuid
                       :type "cartview"
                       :site_id uuid
                       :promo_id uuid
                       :shopper_id uuid
                       :session_id uuid
                       :created_at (to-sql-time (now))
                       :data "" }]
        (db-to-event db-result) => (contains {:event-id uuid })))
