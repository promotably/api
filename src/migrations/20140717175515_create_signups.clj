(ns migrations.20140717175515-create-signups
  (:require [api.db :as db]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140717175515."
  []
  (println "Running migration 20140717175515-create-signups")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     (jdbc/create-table-ddl :email_subscribers
                            [:browser_id "uuid NOT NULL PRIMARY KEY"]
                            [:email "varchar(255) NOT NULL"]
                            [:created_at "timestamp NOT NULL DEFAULT now()"]))))

(defn down
  "Migrates the database down from version 20140717175515."
  []
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands db-con
                         (jdbc/drop-table-ddl :email_subscribers))))
