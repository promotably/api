(ns migrations.20150209152404-add-cart-value-offer-condition
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150209152404."
  []
  (println "migrations.20150209152404-add-cart-value-offer-condition up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE offer_conditions ADD COLUMN cart_value INTEGER")))

(defn down
  "Migrates the database down from version 20150209152404."
  []
  (println "migrations.20150209152404-add-cart-value-offer-condition down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE offer_conditions DROP COLUMN cart_value")))
