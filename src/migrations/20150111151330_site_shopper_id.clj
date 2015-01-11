(ns migrations.20150111151330-site-shopper-id
  (:require [api.db :as db :refer [$db-config]]
            [clojure.java.jdbc :as jdbc]))

(defn up
  "Migrates the database up to version 20150111151330."
  []
  (println "migrations.20150111151330-site-shopper-id up...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE events ADD COLUMN site_shopper_id uuid NOT NULL;"
     "ALTER TABLE promo_redemptions ADD COLUMN site_shopper_id uuid NOT NULL;"
     "DROP FUNCTION upsertEvent(_type text, eventId uuid, siteId uuid, shopperId uuid,
                                             sessionId uuid, promoId uuid, _data json);"
     "CREATE OR REPLACE FUNCTION upsertEvent(_type text, eventId uuid, siteId uuid, shopperId uuid,
                                             siteShopperId uuid, sessionId uuid, promoId uuid, _data json)
      RETURNS void AS $$
      BEGIN
      IF _type = 'trackthankyou' THEN
          IF NOT EXISTS (SELECT 1 FROM events WHERE (events.event_id=eventId)
                                              OR (events.type=_type
                                                  AND events.site_id=siteId
                                                  AND events.shopper_id=shopperId
                                                  AND events.site_shopper_id=siteShopperId
                                                  AND events.session_id=sessionId
                                                  AND json_extract_path_text(events.data, 'order-id')=json_extract_path_text(_data, 'order-id'))) THEN
              INSERT INTO events (event_id, type, site_id, shopper_id, site_shopper_id, session_id, data)
                                 VALUES (eventId, _type, siteId, shopperId, siteShopperId, sessionId, _data);
              NULL;
          END IF;
      ELSIF NOT EXISTS (SELECT 1 FROM events WHERE (events.event_id=eventId)) THEN
          INSERT INTO events (event_id, type, site_id, shopper_id, site_shopper_id, session_id, promo_id, data)
                             VALUES (eventId, _type, siteId, shopperId, siteShopperId, sessionId, promoId, _data);
          NULL;
      END IF;
      END;
      $$ LANGUAGE plpgsql VOLATILE;"
     "DROP FUNCTION  upsertPromoRedemption (eventId uuid, siteId uuid,
          orderId text, promoCode text, _discount numeric, shopperId uuid, sessionId uuid);"
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

(defn down
  "Migrates the database down from version 20150111151330."
  []
  (println "migrations.20150111151330-site-shopper-id down...")
  (jdbc/with-db-connection [db-con @db/$db-config]
    (jdbc/db-do-commands
     db-con
     "ALTER TABLE events DROP COLUMN site_shopper_id;"
     "ALTER TABLE promo_redemptions DROP COLUMN site_shopper_id;"
     "DROP FUNCTION upsertEvent(_type text, eventId uuid, siteId uuid, shopperId uuid,
                                siteShopperId uuid, sessionId uuid, promoId uuid, _data json)"
     "CREATE OR REPLACE FUNCTION upsertEvent(_type text, eventId uuid, siteId uuid, shopperId uuid,
                                             sessionId uuid, promoId uuid, _data json)
      RETURNS void AS $$
      BEGIN
      IF _type = 'trackthankyou' THEN
          IF NOT EXISTS (SELECT 1 FROM events WHERE (events.event_id=eventId)
                                              OR (events.type=_type
                                                  AND events.site_id=siteId
                                                  AND events.shopper_id=shopperId
                                                  AND events.session_id=sessionId
                                                  AND json_extract_path_text(events.data, 'order-id')=json_extract_path_text(_data, 'order-id'))) THEN
              INSERT INTO events (event_id, type, site_id, shopper_id, session_id, data)
                                 VALUES (eventId, _type, siteId, shopperId, sessionId, _data);
              NULL;
          END IF;
      ELSIF NOT EXISTS (SELECT 1 FROM events WHERE (events.event_id=eventId)) THEN
          INSERT INTO events (event_id, type, site_id, shopper_id, session_id, promo_id, data)
                             VALUES (eventId, _type, siteId, shopperId, sessionId, promoId, _data);
          NULL;
      END IF;
      END;
      $$ LANGUAGE plpgsql;"
     "DROP FUNCTION upsertPromoRedemption (eventId uuid, siteId uuid,
          orderId text, promoCode text, _discount numeric, shopperId uuid, siteShopperId uuid, sessionId uuid);"
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
