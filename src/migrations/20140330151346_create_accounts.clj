(ns migrations.20140330151346-create-accounts
  (:require [api.system :refer [system]]
            [clojure.java.jdbc :as jdbc]))

;; BREAKING CHANGE!!!
;; This migration will not create accounts for users already in the
;; database. If any promos are associated with a user, they will be
;; stranded

(defn up
  "Migrates the database up to version 20140330151346."
  []
  (let [db-config (get-in system [:app :database])]
    (jdbc/with-db-connection [db-con db-config]
      (jdbc/db-do-commands
       db-con
       (jdbc/create-table-ddl :accounts
                              [:id "serial8 primary key"]
                              [:company_name "varchar(255)"]
                              [:created_at "timestamp"]
                              [:updated_at "timestamp"])
       "ALTER TABLE users ADD COLUMN account_id int8"
       "CREATE INDEX users_account_id_idx ON users ( account_id )"
       "ALTER TABLE promos RENAME COLUMN user_id TO account_id"))))

(defn down
  "Migrates the database down from version 20140330151346."
  []
  (let [db-config (get-in system [:app :database])]
    (jdbc/with-db-connection [db-con db-config]
      (jdbc/db-do-commands
       db-con
       (jdbc/drop-table-ddl :accounts)
       "DROP INDEX users_account_id_idx"
       "ALTER TABLE users DROP COLUMN account_id"
       "ALTER TABLE promos RENAME COLUMN account_id TO user_id"))))
