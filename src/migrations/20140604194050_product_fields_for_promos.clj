(ns migrations.20140604194050-product-fields-for-promos
  (:require [api.system :refer [system]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140604194050."
  []
  (let [db-config (get-in system [:app :database])]
    (println "migrations.20140604194050-product-fields-for-promos up...")
    (jdbc/with-db-connection [db-con db-config]
      (jdbc/db-do-commands
       db-con
       "ALTER TABLE promos ADD COLUMN product_ids varchar(255)[],
                         ADD COLUMN exclude_product_ids varchar(255)[],
                         ADD COLUMN product_categories varchar(255)[],
                         ADD COLUMN exclude_product_categories varchar(255)[]"))))

(defn down
  "Migrates the database down from version 20140604194050."
  []
  (let [db-config (get-in system [:app :database])]
    (println "migrations.20140604194050-product-fields-for-promos down...")
    (jdbc/with-db-connection [db-con db-config]
      (jdbc/db-do-commands
       db-con
       "ALTER TABLE promos DROP COLUMN product_ids,
                         DROP COLUMN exclude_product_ids,
                         DROP COLUMN product_categories,
                         DROP COLUMN exclude_product_categories"
       "DELETE FROM migrations WHERE version='20140604194050'"))))
