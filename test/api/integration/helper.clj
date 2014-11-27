(ns api.integration.helper
  (:require [api.fixtures.basic :as base]
            [api.route :as route]
            [korma.db :as kdb]
            [api.q-fix :as qfix]
            [api.db :as db]
            [clj-time.coerce :refer (to-sql-time)]
            [clojure.java.jdbc :as jdbc]
            [clj-time.core :refer (now)]
            [drift.execute :as drift]
            [midje.sweet :refer :all]
            [ring.adapter.jetty :refer (run-jetty)]))

(defn migrate-down
  []
  (with-out-str (drift.execute/migrate 0 [])))

(defn migrate-up
  []
  (with-out-str (drift.execute/migrate Long/MAX_VALUE [])))

(defn load-fixture-set
  [fset]
  (let [config (-> route/current-system :config :database)]
    (jdbc/with-db-transaction [t-con (kdb/postgres config)]
      (qfix/load fset
                 (fn [table-name val-map]
                   (let [result (jdbc/insert! t-con table-name val-map)]
                     (-> result first :id)))))))
