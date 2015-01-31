(ns migrations.20150127065911-alter-offer-conditions-w-cart-additions
  (:require [api.db :as db :refer [$db-config]] 
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150127065911."
  []
  (println "migrations.20150127065911-alter-offer-conditions-w-cart-additions up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE offer_conditions ADD COLUMN num_cart_adds INTEGER")))
(defn down
  "Migrates the database down from version 20150127065911."
  []
  (println "migrations.20150127065911-alter-offer-conditions-w-cart-additions down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE offer_conditions DROP COLUMN num_cart_adds")))