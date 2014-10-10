(ns migrations.20140531182544-moar-fields-for-promos
  (:require [api.system :refer [system]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140531182544."
  []
  (let [db-config (get-in system [:app :database])]
    (jdbc/with-db-connection [db-con db-config]
      (jdbc/db-do-commands
       db-con
       "ALTER TABLE promos ADD COLUMN uuid uuid NOT NULL DEFAULT uuid_generate_v4(),
                         ADD COLUMN incept_date timestamp,
                         ADD COLUMN expiry_date timestamp,
                         ADD COLUMN individual_use boolean,
                         ADD COLUMN exclude_sale_items boolean,
                         ADD COLUMN max_usage_count int4,
                         ADD COLUMN current_usage_count int4 DEFAULT 0,
                         ADD COLUMN type varchar(255),
                         ADD COLUMN active boolean DEFAULT false"))))

(defn down
  "Migrates the database down from version 20140531182544."
  []
  (let [db-config (get-in system [:app :database])]
    (jdbc/with-db-connection [db-con db-config]
      (jdbc/db-do-commands
       db-con
       "ALTER TABLE promos DROP COLUMN uuid"))))
