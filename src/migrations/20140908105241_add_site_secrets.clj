(ns migrations.20140908105241-add-site-secrets
  (:require [api.system :refer [system]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140908105241."
  []
  (let [db-config (get-in system [:app :database])]
    (println "migrations.20140908105241-add-site-secrets up...")
    (jdbc/with-db-connection [db-con db-config]
      (jdbc/db-do-commands
       db-con
       "ALTER TABLE sites ADD COLUMN site_code TEXT,
                        ADD COLUMN api_secret UUID,
                        ALTER COLUMN name DROP NOT NULL"))))

(defn down
  "Migrates the database down from version 20140908105241."
  []
  (let [db-config (get-in system [:app :database])]
    (println "migrations.20140908105241-add-site-secrets down...")
    (jdbc/with-db-connection [db-con db-config]
      (jdbc/db-do-commands
       db-con
       "ALTER TABLE sites DROP COLUMN site_code,
                        DROP COLUMN api_secret,
                        ALTER COLUMN name SET NOT NULL"))))
