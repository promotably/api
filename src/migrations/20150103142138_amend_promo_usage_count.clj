(ns migrations.20150103142138-amend-promo-usage-count
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150103142138."
  []
  (println "migrations.20150103142138-amend-promo-usage-count up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "CREATE OR REPLACE FUNCTION promoUsageCount (siteId uuid, promoId uuid)
      RETURNS integer AS $total$
      declare
          total integer;
      BEGIN
          SELECT count(pr.id) into total
              FROM promo_redemptions pr
              JOIN promos p ON pr.promo_code=p.code
              JOIN sites s ON s.id=p.site_id
              WHERE s.site_id=siteId
              AND pr.site_id=siteId
              AND p.uuid=promoId;
          RETURN total;
      END
      $total$ LANGUAGE plpgsql STABLE;"
     "CREATE OR REPLACE FUNCTION promoTotalDiscounts (siteId uuid, promoId uuid)
      RETURNS numeric AS $total$
      declare total numeric;
      BEGIN
          SELECT COALESCE(SUM(pr.discount), 0.0) INTO total
              FROM promo_redemptions pr
              JOIN promos p ON pr.promo_code=p.code
              JOIN sites s ON s.id=p.site_id
              WHERE s.site_id=siteId
              AND pr.site_id=siteId
              AND p.uuid=promoId;
          RETURN total;
      END
      $total$ LANGUAGE plpgsql STABLE;")))

(defn down
  "Migrates the database down from version 20150103142138."
  []
  (println "migrations.20150103142138-amend-promo-usage-count down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "CREATE OR REPLACE FUNCTION promoUsageCount (siteId uuid, promoId uuid)
      RETURNS integer AS $total$
      declare
          total integer;
      BEGIN
          SELECT count(*) into total FROM EVENTS WHERE type='trackthankyou' AND site_id=siteId AND promo_id=promoId;
          RETURN total;
      END
      $total$ LANGUAGE plpgsql;"
     "CREATE OR REPLACE FUNCTION promoTotalDiscounts (siteId uuid, promoId uuid)
      RETURNS numeric AS $total$
      declare total numeric;
      BEGIN
          SELECT COALESCE(SUM(pr.discount), 0.0) INTO total
              FROM promo_redemptions pr
              JOIN promos p ON p.code = pr.promo_code
              JOIN sites s ON s.id = p.site_id
              WHERE s.site_id=siteId
              AND p.uuid=promoId;
          RETURN total;
      END
      $total$ LANGUAGE plpgsql STABLE;")))
