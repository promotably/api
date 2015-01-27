(ns migrations.20150127094525-multiple-accounts-per-user
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150127094525."
  []
  (println "migrations.20150127094525-multiple-accounts-per-user up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     (jdbc/create-table-ddl :users_accounts
                            [:users_id "INTEGER REFERENCES users (id)"]
                            [:accounts_id "INTEGER REFERENCES accounts (id)"])
     "CREATE INDEX users_accounts_users_id ON users_accounts (users_id)"
     "CREATE INDEX users_accounts_accounts_id ON users_accounts (accounts_id)"
     "ALTER TABLE users DROP COLUMN account_id")))

(defn down
  "Migrates the database down from version 20150127094525."
  []
  (println "migrations.20150127094525-multiple-accounts-per-user down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE users ADD COLUMN account_id INTEGER REFERENCES accounts (id)"
     "DROP TABLE users_accounts")))
