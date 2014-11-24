(ns api.integration.helper
  (:require [api.fixtures.basic :as base]
            [api.system :as system]
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

(let [test-server (atom nil)]
  (defn start-test-server
    []
    (system/init (api.config/lookup))
    (system/start)
    (migrate-down)
    (migrate-up)
    (reset! test-server
           (doto (Thread.
                  (fn [] (run-jetty api.system/servlet-handler {:port 3000})))
             (.start))))

  (defn stop-test-server
    []
    (.stop @test-server)))
