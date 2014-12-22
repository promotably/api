(ns migrations.20141222123948-cascading-foreign-keys
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20141222123948."
  []
  (println "migrations.20141222123948-cascading-foreign-keys up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE promo_conditions DROP CONSTRAINT
      conditions_promo_id_fkey"
     "ALTER TABLE promo_conditions ADD
      CONSTRAINT conditions_promo_id_fkey FOREIGN KEY (promo_id)
      REFERENCES promos (id) MATCH SIMPLE ON UPDATE NO ACTION ON DELETE
      CASCADE"
     "ALTER TABLE linked_products DROP CONSTRAINT
      linked_products_promo_id_fkey"
     "ALTER TABLE linked_products
      ADD CONSTRAINT linked_products_promo_id_fkey FOREIGN KEY (promo_id)
      REFERENCES promos (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE CASCADE"
     "ALTER TABLE offer_conditions DROP CONSTRAINT
      offer_conditions_offer_id_fkey"
     "ALTER TABLE offer_conditions
      ADD CONSTRAINT offer_conditions_offer_id_fkey FOREIGN KEY (offer_id)
      REFERENCES offers (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE CASCADE")))

(defn down
  "Migrates the database down from version 20141222123948."
  []
  (println "migrations.20141222123948-cascading-foreign-keys down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE promo_conditions DROP CONSTRAINT
      conditions_promo_id_fkey"
     "ALTER TABLE promo_conditions ADD CONSTRAINT
      conditions_promo_id_fkey FOREIGN KEY (promo_id) REFERENCES
      promos (id) MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION"
     "ALTER TABLE linked_products DROP CONSTRAINT
      linked_products_promo_id_fkey"
     "ALTER TABLE linked_products ADD CONSTRAINT
      linked_products_promo_id_fkey FOREIGN KEY (promo_id) REFERENCES
      promos (id) MATCH SIMPLE ON UPDATE NO ACTION ON DELETE NO ACTION"
     "ALTER TABLE offer_conditions DROP CONSTRAINT
      offer_conditions_offer_id_fkey"
     "ALTER TABLE offer_conditions
      ADD CONSTRAINT offer_conditions_offer_id_fkey FOREIGN KEY (offer_id)
      REFERENCES offers (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION")))
