(ns migrations.20141214120906-events-uuids
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20141214120906."
  []
  (println "migrations.20141214120906-events-uuids up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE events DROP COLUMN site_id,
                         DROP COLUMN promo_id"
     "ALTER TABLE events ADD COLUMN site_id uuid NOT NULL,
                         ADD COLUMN promo_id uuid")))

(defn down
  "Migrates the database down from version 20141214120906."
  []
  (println "migrations.20141214120906-events-uuids down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE events DROP COLUMN site_id,
                         DROP COLUMN promo_id"
     "ALTER TABLE events ADD COLUMN site_id BIGINT NOT NULL,
                         ADD COLUMN promo_id BIGINT")))
