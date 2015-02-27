(ns migrations.20150227000508-create-offer-qualifications-table
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150227000508."
  []
  (println "migrations.20150227000508-create-offer-qualifications-table up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     (jdbc/create-table-ddl :offer_qualifications
                            [:id "bigserial primary key"]
                            [:event_id "uuid not null"]
                            [:site_id "uuid not null"]
                            [:site_shopper_id "uuid not null"]
                            [:shopper_id "uuid"]
                            [:session_id "uuid"]
                            [:offer_id "uuid not null"]
                            [:created_at "timestamp default current_timestamp"])
     "CREATE INDEX offer_qualifications_search_idx ON offer_qualifications (site_id, site_shopper_id)"
     "CREATE INDEX offer_qualifications_event_id_idx ON offer_qualifications (event_id)")))

(defn down
  "Migrates the database down from version 20150227000508."
  []
  (println "migrations.20150227000508-create-offer-qualifications-table down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     (jdbc/drop-table-ddl :offer_qualifications))))
