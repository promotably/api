(ns migrations.20150111114113-drop-total-discounts-function
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150111114113."
  []
  (println "migrations.20150111114113-drop-total-discounts-function up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "DROP FUNCTION promoTotalDiscounts (siteId uuid, promoId uuid);")))

(defn down
  "Migrates the database down from version 20150111114113."
  []
  (println "migrations.20150111114113-drop-total-discounts-function down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "CREATE OR REPLACE FUNCTION promoTotalDiscounts (siteId uuid, promoId uuid)
      RETURNS numeric AS $total$
      declare total numeric;
      BEGIN
          SELECT COALESCE(SUM(pr.discount), 0.0) INTO total
              FROM promo_redemptions pr
              JOIN promos p ON p.code = pr.promo_code
              JOIN sites s ON s.id = p.site_id
              WHERE s.uuid=siteId
              AND p.uuid=promoId;
          RETURN total;
      END
      $total$ LANGUAGE plpgsql STABLE;")))
