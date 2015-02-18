(ns api.integration.db
  (:require
   [clojure.java.jdbc :as jdbc]
   [api.integration.helper :refer :all]
   [api.fixtures.basic :as base]
   [api.fixtures.common :refer [site-uuid]]
   [api.route :as route]
   [api.db :as db]
   [api.system :as system]
   [api.core :as core]
   [clj-http.client :as client]
   [midje.sweet :refer :all]))

(against-background [(before :contents
                             (do (when (nil? system/current-system)
                                   (core/go {:port 3000 :repl-port 55555}))
                                 (migrate-down)
                                 (migrate-up)
                                 (load-fixture-set base/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

  (future-facts "upsertPromoRedmption"
    (jdbc/with-db-connection [c @db/$db-config]
      (let [conn (jdbc/get-connection c)]
        (let [statement (doto (.prepareCall conn
                                            "SELECT upsertPromoRedemption(?,?,?,?,?,?,?,?);")
                          (.setObject 1 (java.util.UUID/randomUUID)) ;; event-id
                          (.setObject 2 site-uuid)
                          (.setObject 3 "1")
                          (.setObject 4 "EASTER")
                          (.setObject 5 (BigDecimal. "10"))
                          (.setObject 6 (java.util.UUID/randomUUID)) ;; shopper
                          (.setObject 7 (java.util.UUID/randomUUID)) ;; site-shopper
                          (.setObject 8 (java.util.UUID/randomUUID)));; session
              result (.execute statement)]
          result => truthy
          (count (jdbc/query c ["select * from promo_redemptions"])) => 1)))))
