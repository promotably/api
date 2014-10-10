(ns migrations.20140310191948-create-users-table
  (:require [api.system :refer [system]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140310191948."
  []
  (let [db-config (get-in system [:app :database])]
    (jdbc/with-db-connection [db-con db-config]
      (jdbc/db-do-commands
       db-con
       (jdbc/create-table-ddl :users
                              [:id "serial8 primary key"]
                              [:username "varchar(255) NOT NULL"]
                              [:email "varchar(255) NOT NULL"]
                              [:crypted_password "varchar(255) NOT NULL"]
                              [:phone "varchar(255)"]
                              [:company_name "varchar(255)"]
                              [:job_title "varchar(255)"]
                              [:created_at "timestamp"]
                              [:last_logged_in_at "timestamp"])
       "CREATE UNIQUE INDEX username_idx ON users ( username )"
       "CREATE UNIQUE INDEX email_idx ON users ( email )"))))

(defn down
  "Migrates the database down from version 20140310191948."
  []
  (let [db-config (get-in system [:app :database])]
    (jdbc/db-do-commands db-config
                         (jdbc/drop-table-ddl :users))))
