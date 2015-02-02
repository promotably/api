(ns api.models.event
  (:require [api.entities :refer :all]
            [clj-time.core :refer [now minus days]]
            [clj-time.coerce :refer [to-sql-date]]
            [clojure.set :refer [rename-keys]]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
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


