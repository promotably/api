(ns api.integration.helper
  (:require [api.fixtures.basic :as base]
            [api.q-fix :as qfix]
            [api.db :as db]
            [clj-time.coerce :refer (to-sql-time)]
            [clojure.java.jdbc :as jdbc]
            [clj-time.core :refer (now)]
            [drift.execute :as drift]
            [midje.sweet :refer :all]))

(defn migrate-down
  []
  (drift.execute/migrate 0 []))

(defn migrate-up
  []
  (drift.execute/migrate Long/MAX_VALUE []))

(defn load-fixture-set
  [fset]
  (jdbc/with-db-transaction [t-con @db/$db-config]
    (qfix/load fset
               (fn [table-name val-map]
                 (let [result (jdbc/insert! t-con table-name val-map)]
                   (-> result first :id))))))

