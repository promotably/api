(ns migrations.20150219054127-alter-upsertpromoredemptions-add-control-group
  (:require [api.db :as db :refer [$db-config]] 
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150219054127."
  []
  (println "migrations.20150219054127-alter-upsertpromoredemptions-add-control-group up...")
  (jdbc/with-db-connection [db-con @$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE promo_redemptions ADD COLUMN control_group BOOLEAN DEFAULT false;")))

(defn down
  "Migrates the database down from version 20150219054127."
  []
  (println "migrations.20150219054127-alter-upsertpromoredemptions-add-control-group down...")
  (jdbc/with-db-connection [db-con @$db-config]
                           (jdbc/db-do-commands
                             db-con
                             "ALTER TABLE promo_redemptions DROP COLUMN control_group;")))
