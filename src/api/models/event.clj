(ns api.models.event
  (:require [clojure.tools.logging :as log]
            [clj-time.format]
            [clj-time.core :refer [now minus days]]
            [clj-time.coerce :refer [to-sql-date to-sql-time]]
            [clojure.set :refer [rename-keys]]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
            [api.entities :refer :all]
            [api.util :refer [hyphenify-key assoc*]]
            [api.lib.schema :refer :all]
            [korma.core :refer :all]
            [schema.core :as s]
            [schema.macros :as sm]))

(defn db-to-event
  "Translates a database result to a map"
  [r]
  (let [ks (keys r)]
    (rename-keys r (zipmap ks (map hyphenify-key ks)))))

(sm/defn count-shopper-events-by-days
  [site-shopper-uuid :- s/Uuid event-type :- s/Str days-ago :- s/Int]
  (let [r (select events
            (aggregate (count :*) :cnt)
            (where {:site_shopper_id site-shopper-uuid
                    :type event-type
                    :created_at [>= (to-sql-date (minus (now) (days days-ago)))]}))
        count (get (first r) :cnt 0)]
    count))

(sm/defn ^:always-validate find-by-id :- (s/maybe BaseEvent)
         [id :- s/Int]
         (db-to-event
           (first (select events (where {:id id})))))

(sm/defn orders-since
  "Count the number of unique orders (as defined by site-id/order-id
  tuple) placed by a site-shopper-id in a period going back days-ago."
  [site-id :- s/Uuid site-shopper-id :- s/Uuid days-ago :- s/Int]
  (let [then (to-sql-date (minus (now) (days days-ago)))]
    (count
     (exec-raw [(str "SELECT data->>'order-id' as order_id, "
                     "  count(events.data) as c " ;; needed because of group by
                     "FROM events "
                     "WHERE "
                     "  (site_id = ? AND "
                     "   site_shopper_id = ? AND "
                     "   type = 'thankyou' AND "
                     "   (to_timestamp(data->>'order-date', 'YYYY-MM-DD HH24:MI:SS') "
                     "     BETWEEN ?::timestamp AND now()::timestamp) AND "
                     "   ((data->>'order-id') IS NOT NULL)) "
                     "GROUP BY events.data->>'order-id'")
                [site-id site-shopper-id then]] :results))))


(sm/defn shopper-events
  "Returns a collection of all events for the specified site, site shopper, and type.
   Defaults to 1 day look-back"
  [site-id :- s/Uuid site-shopper-id :- s/Uuid event-type :- s/Str days-ago :- s/Int]
  (let [since (to-sql-time (minus (now) (days days-ago)))]
    (map db-to-event
         (select events
                 (where {:site_id site-id
                         :site_shopper_id site-shopper-id
                         :type event-type
                         :created_at [>= since]})))))

(sm/defn last-event
  "Get the last event for the site/site-shopper/event-type tuple."
  [site-id :- s/Uuid site-shopper-id :- s/Uuid event-type :- s/Str]
  (first (select events
                 (where {:site_id site-id
                         :site_shopper_id site-shopper-id
                         :type event-type})
                 (order :created_at :DESC)
                 (limit 1))))

(sm/defn last-event-by-session-id
  "Get the last event for the session-id/event-type tuple."
  [session-id :- s/Uuid event-type :- s/Str]
  (first (select events
                 (where {:session_id session-id
                         :type event-type})
                 (order :created_at :DESC)
                 (limit 1))))

;; (clojure.pprint/pprint (last-event #uuid "5669de1d-cc61-4590-9ef6-5cab58369df2" #uuid "001fd699-9d50-4b7c-af3b-3e022d379647" "thankyou"))

(sm/defn item-count-in-last-order
  "Get number of items in the shopper's last order."
  [site-id :- s/Uuid site-shopper-id :- s/Uuid]
  (let [e (last-event site-id site-shopper-id "thankyou")]
    (if e
      (apply + (map :quantity (-> e :data :cart-items)))
      0)))

(sm/defn value-of-last-order
  "Get total of the shopper's last order."
  [site-id :- s/Uuid site-shopper-id :- s/Uuid]
  (let [e (last-event site-id site-shopper-id "thankyou")]
    (if e
      (-> e :data :total bigint)
      0)))

(sm/defn discount-last-order
  "Get total of the shopper's last order."
  [site-id :- s/Uuid site-shopper-id :- s/Uuid]
  (let [e (last-event site-id site-shopper-id "thankyou")]
    (if e
      (-> e :data :discount bigint)
      0)))

;; (discount-last-order #uuid "5669de1d-cc61-4590-9ef6-5cab58369df2" #uuid "001fd699-9d50-4b7c-af3b-3e022d379647")

(sm/defn find-offer
  "Find any (possibly redeemed, or expired) offer with the code."
  [site-id :- s/Uuid code :- s/Str]
  (first (exec-raw [(str "SELECT events.* "
                         "FROM events "
                         "WHERE "
                         "  (site_id = ? AND "
                         "   type = 'offer-made' AND "
                         "   data->>'code' = ?)"
                         "ORDER BY events.created_at DESC "
                         "LIMIT 1")
                    [site-id code]] :results)))

(sm/defn find-outstanding-offer
  "Find an unredeemed, unexpired offer with the code."
  [cloudwatch-recorder site-id :- s/Uuid code :- s/Str]
  (let [offer (find-offer site-id code)
        expiry (-> offer :data :expiry clj-time.format/parse)
        redemption (first (select promo-redemptions
                                  (where {:site_id site-id
                                          :promo_code code})
                                  (order :created_at :DESC)
                                  (limit 1)))]
    (if (and (not redemption) offer)
      (if (clj-time.core/before? (now) expiry)
        offer
        (do
          (log/logf :debug "Expired offer")
          (cloudwatch-recorder "offer-expired" 1 :Count)
          nil)))))

;; (find-outstanding-offer #uuid "9be8a905-498d-4a8e-ba50-397e2d5f5275" "XP9HEW")

