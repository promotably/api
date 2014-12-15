(ns migrations.20141211160018-events-table
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20141211160018."
  []
  (println "migrations.20141211160018-events-table up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     (jdbc/create-table-ddl :events
                            [:id "BIGSERIAL PRIMARY KEY"]
                            [:event_id "uuid NOT NULL"]
                            [:type "TEXT NOT NULL"]
                            [:site_id "uuid NOT NULL"]
                            [:promo_id "uuid"]
                            [:shopper_id "uuid NOT NULL"]
                            [:session_id "uuid NOT NULL"]
                            [:created_at "timestamp DEFAULT current_timestamp"]
                            [:data "JSON"])
     "CREATE INDEX events_site_id_shopper_id_idx ON events(site_id, shopper_id)"
     "CREATE INDEX events_site_id_type_idx ON events(site_id, type)"
     "CREATE INDEX events_site_id_promo_id_idx ON events(site_id, promo_id)"
     "CREATE INDEX events_site_id_promo_id_type_idx ON events(site_id, promo_id, type)"
     "CREATE INDEX events_site_id_shopper_id_type_idx ON events(site_id, shopper_id, type)")))


(defn down
  "Migrates the database down from version 20141211160018."
  []
  (println "migrations.20141211160018-events-table down...")
  (jdbc/db-do-commands @db/$db-config
                       (jdbc/drop-table-ddl :events)))
