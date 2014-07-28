(ns migrations.20140725104114-add-user-social-id
  (:require [api.db :as db]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140725104114."
  []
  (println "migrations.20140725104114-add-user-social-id up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE users ADD COLUMN user_social_id TEXT"
     "CREATE INDEX users_user_social_id_idx ON users ( user_social_id )")))

(defn down
  "Migrates the database down from version 20140725104114."
  []
  (println "migrations.20140725104114-add-user-social-id down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE users DROP COLUMN user_social_id")))
