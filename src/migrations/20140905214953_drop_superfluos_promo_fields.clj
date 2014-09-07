(ns migrations.20140905214953-drop-superfluos-promo-fields
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20140905214953."
  []
  (println "migrations.20140905214953-drop-superfluos-promo-fields up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE promos DROP COLUMN incept_date,
                         DROP COLUMN expiry_date,
                         DROP COLUMN individual_use,
                         DROP COLUMN exclude_sale_items,
                         DROP COLUMN max_usage_count,
                         DROP COLUMN current_usage_count,
                         DROP COLUMN type,
                         DROP COLUMN free_shipping,
                         DROP COLUMN minimum_cart_amount,
                         DROP COLUMN minimum_product_amount,
                         DROP COLUMN usage_limit_per_user,
                         DROP COLUMN product_ids,
                         DROP COLUMN exclude_product_ids,
                         DROP COLUMN product_categories,
                         DROP COLUMN exclude_product_categories,
                         DROP COLUMN limit_usage_to_x_items")))

(defn down
  "Migrates the database down from version 20140905214953."
  []
  (println "migrations.20140905214953-drop-superfluos-promo-fields down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE promos ADD COLUMN incept_date timestamp,
                         ADD COLUMN expiry_date timestamp,
                         ADD COLUMN individual_use boolean,
                         ADD COLUMN exclude_sale_items boolean,
                         ADD COLUMN max_usage_count int4,
                         ADD COLUMN current_usage_count int4 DEFAULT 0,
                         ADD COLUMN type varchar(255),
                         ADD COLUMN free_shipping boolean DEFAULT false,
                         ADD COLUMN minimum_cart_amount NUMERIC(16,4) DEFAULT 0,
                         ADD COLUMN minimum_product_amount NUMERIC(16,4) DEFAULT 0,
                         ADD COLUMN usage_limit_per_user INTEGER DEFAULT 0,
                         ADD COLUMN product_ids varchar(255)[],
                         ADD COLUMN exclude_product_ids varchar(255)[],
                         ADD COLUMN product_categories varchar(255)[],
                         ADD COLUMN exclude_product_categories varchar(255)[],
                         ADD COLUMN limit_usage_to_x_items INTEGER")))
