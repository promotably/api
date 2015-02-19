(ns migrations.20150218133308-fix-promo-redemption-function
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150218133308."
  []
  (println "migrations.20150218133308-fix-promo-redemption-function up...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "DROP FUNCTION  upsertPromoRedemption (eventId uuid, siteId uuid,
          orderId text, promoCode text, _discount numeric, shopperId uuid, siteShopperId uuid, sessionId uuid);"
     "CREATE OR REPLACE FUNCTION upsertPromoRedemption (eventId uuid, siteId uuid,
          orderId text, promoCode text, _discount numeric, shopperId uuid, siteShopperId uuid, sessionId uuid)
      RETURNS void AS $$
      DECLARE promoId bigint;
      BEGIN

      SELECT p.id INTO promoId FROM promos p
          JOIN sites s ON p.site_id=s.id
          WHERE p.code=promoCode
          AND s.site_id=siteId;

      IF NOT EXISTS (SELECT 1 FROM promo_redemptions
                         WHERE site_id=siteId
                         AND order_id=orderId
                         AND site_shopper_id=siteShopperId
                         AND promo_id=promoId) THEN
          INSERT INTO promo_redemptions (event_id, site_id, order_id, promo_id, promo_code,
                                         discount, shopper_id, site_shopper_id, session_id)
              VALUES (eventId, siteId, orderId, promoId, promoCode, _discount, shopperId,
                      siteShopperId, sessionId);
      END IF;
      NULL;
      END;
      $$ LANGUAGE plpgsql VOLATILE;")))

(defn down
  "Migrates the database down from version 20150218133308."
  []
  (println "migrations.20150218133308-fix-promo-redemption-function down...")
  (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "DROP FUNCTION  upsertPromoRedemption (eventId uuid, siteId uuid,
          orderId text, promoCode text, _discount numeric, shopperId uuid, siteShopperId uuid, sessionId uuid);"
     "CREATE OR REPLACE FUNCTION upsertPromoRedemption (eventId uuid, siteId uuid,
          orderId text, promoCode text, _discount numeric, shopperId uuid, siteShopperId uuid, sessionId uuid)
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
                         AND site_shopper_id=siteShopperId
                         AND promo_id=promoId) THEN
          INSERT INTO promo_redemptions (event_id, site_id, order_id, promo_id, promo_code,
                                         discount, shopper_id, site_shopper_id, session_id)
              VALUES (eventId, siteId, orderId, promoId, promoCode, _discount, shopperId,
                      siteShopperId, sessionId);
      END IF;
      NULL;
      END;
      $$ LANGUAGE plpgsql VOLATILE;")))
