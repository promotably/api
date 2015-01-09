(ns ^{:author "tsteffes@promotably.com"
      :doc "This migration changes the promo_id field on the promo_migrations table
           from a uuid to a bigint. After the migration is applied, promo_id will reference
           the id field on the promos table.

           Also, this migration changes the upsertPromoRedemption function to perform a lookup
           of the id field on the promos table, since it is not known at the time the event is
           recorded. If no promo is found with the given promo code and site id, or if more than
           one promo is found (should not be possible, but I've been wrong before), the
           upsertPromoRedemption function will throw an exception."}
  migrations.20150108230409-alter-promo-redemptions
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150108230409."
  []
  (println "migrations.20150108230409-alter-promo-redemptions up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE promo_redemptions DROP COLUMN promo_id"
     "ALTER TABLE promo_redemptions ADD COLUMN promo_id bigint"
     "CREATE INDEX promo_id_promo_redemptions_idx ON promo_redemptions (promo_id)"
     "CREATE OR REPLACE FUNCTION upsertPromoRedemption (eventId uuid, siteId uuid,
          orderId text, promoCode text, _discount numeric, shopperId uuid, sessionId uuid)
      RETURNS void AS $$
      DECLARE promoId bigint;
      BEGIN

      SELECT p.id INTO STRICT promoId FROM promos p
          JOIN sites ON p.site_id=s.id
          WHERE p.code=promoCode
          AND s.uuid=siteId;
      EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE EXCEPTION 'promo % not found', promoCode;
        WHEN TOO_MANY_ROWS THEN
            RAISE EXCEPTION 'promo % not unique', promoCode;

      IF NOT EXISTS (SELECT 1 FROM promo_redemptions
                         WHERE site_id=siteId
                         AND order_id=orderId
                         AND promo_id=promoId) THEN
          INSERT INTO promo_redemptions (event_id, site_id, order_id, promo_id, promo_code,
                                         discount, shopper_id, session_id)
              VALUES (eventId, siteId, orderId, promoId, promoCode, _discount, shopperId,
                      sessionId);
      END IF;
      NULL;
      END;
      $$ LANGUAGE plpgsql VOLATILE;")))

(defn down
  "Migrates the database down from version 20150108230409."
  []
  (println "migrations.20150108230409-alter-promo-redemptions down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE promo_redemptions DROP COLUMN promo_id"
     "ALTER TABLE promo_redemptions ADD COLUMN promo_id uuid"
     "CREATE OR REPLACE FUNCTION upsertPromoRedemption (eventId uuid, siteId uuid,
          orderId text, promoCode text, _discount numeric, shopperId uuid, sessionId uuid)
      RETURNS void AS $$
      BEGIN
      IF NOT EXISTS (SELECT 1 FROM promo_redemptions
                         WHERE site_id=siteId
                         AND order_id=orderId
                         AND promo_code=promoCode) THEN
          INSERT INTO promo_redemptions (event_id, site_id, order_id, promo_code,
                                         discount, shopper_id, session_id)
              VALUES (eventId, siteId, orderId, promoCode, _discount, shopperId,
                      sessionId);
      END IF;
      NULL;
      END;
      $$ LANGUAGE plpgsql;")))
