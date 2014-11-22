(ns api.integration.basic
  (:require [api.fixtures.basic :as base]
            [api.db :as db]
            [api.integration.helper :refer :all]
            [midje.sweet :refer :all]))

(background (around :facts
                    (do (db/init!)
                        (migrate-down)
                        (migrate-up)
                        (load-fixture-set base/fixture-set)
                        ?form
                        (migrate-down))))

(fact "..."
  (let [foo 1]
    nil => truthy))

