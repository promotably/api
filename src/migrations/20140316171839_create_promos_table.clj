(ns migrations.20140316171839-create-promos-table
  (:require [api.db :as db]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140316171839."
  []
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     (jdbc/create-table-ddl :promos
                            [:id "serial8 primary key"]
                            [:user_id "int8 NOT NULL"]
                            [:name "varchar(255) NOT NULL"]
                            [:code "varchar(255) NOT NULL"]
                            [:created_at "timestamp"]
                            [:updated_at "timestamp"])
     "CREATE UNIQUE INDEX user_code_idx ON promos ( user_id, code )"
     "CREATE INDEX code_idx ON promos ( code )")))

(defn down
  "Migrates the database down from version 20140316171839."
  []
  (jdbc/db-do-commands @db/$db-config
                       (jdbc/drop-table-ddl :promos)))
