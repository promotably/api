(ns migrations.20140525130709-create-sites
  (:require [api.db :as db]
            [clojure.java.jdbc :as jdbc]))

;; BREAKING CHANGE!!
;; This migration will not create sites for accounts already in the
;; database. If any promos are associated with an accounbt, they will
;; be stranded.

(defn up
  "Migrates the database up to version 20140525130709."
  []
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     (jdbc/create-table-ddl :sites
                            [:id "serial8 primary key"]
                            [:account_id "int8 NOT NULL"]
                            [:name "varchar(255) NOT NULL"]
                            [:created_at "timestamptz DEFAULT (now() at time zone 'utc') NOT NULL"]
                            [:updated_at "timestamptz DEFAULT (now() at time zone 'utc') NOT NULL"])
     "DROP INDEX user_code_idx"
     "ALTER TABLE promos RENAME COLUMN account_id TO site_id"
     "CREATE UNIQUE INDEX promos_code_site_id_idx ON promos ( site_id, code)"
     "CREATE INDEX sites_account_id_idx ON sites ( account_id )")))

(defn down
  "Migrates the database down from version 20140525130709."
  []
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     (jdbc/drop-table-ddl :sites)
     "DROP INDEX promos_code_site_id_idx"
     "ALTER TABLE promos RENAME COLUMN site_id TO account_id"
     "CREATE UNIQUE INDEX user_code_idx ON promos ( account_id, code )")))
