(ns api.integration.helper
  (:require
   [api.fixtures.basic :as base]
   [api.route :as route]
   [api.system :as system]
   [korma.db :as kdb]
   [api.q-fix :as qfix]
   [api.db :as db]
   [clj-time.coerce :refer (to-sql-time)]
   [clojure.java.jdbc :as jdbc]
   [clojure.walk :refer [postwalk]]
   [clj-time.core :refer (now)]
   [drift.execute :as drift]
   [midje.sweet :refer :all]
   [clj-http.client :as client]
   [clojure.data.json :as json]
   [ring.adapter.jetty :refer (run-jetty)]))

(defn migrate-down
  []
  (with-out-str (drift.execute/migrate 0 [])))

(defn migrate-up
  []
  (with-out-str (drift.execute/migrate Long/MAX_VALUE [])))

(defn load-fixture-set
  [fset]
  (let [config (-> system/current-system :config :database)]
    (jdbc/with-db-transaction [t-con (kdb/postgres config)]
      (qfix/load fset
                 (fn [table-name val-map]
                   (let [xformed (postwalk #(if (fn? %) (% t-con) %)
                                           val-map)]
                   (let [result (jdbc/insert! t-con table-name xformed)]
                     (-> result first :id))))))))

(comment
  (let [config (-> system/current-system :config :database)]
    (jdbc/with-db-transaction [t-con (kdb/postgres config)]
      (let [conn (jdbc/get-connection t-con)
            x
            result (jdbc/insert! t-con "promo_conditions" {"promo_id" 8
                                                           "uuid" (java.util.UUID/randomUUID)
                                                           "type" "product_ids"
                                                           "product_ids" x})]
        (-> result first :id))))

)

(defn create-promo
  [new-promo]
  (client/post "http://localhost:3000/api/v1/promos"
               {:body (json/write-str new-promo)
                :content-type :json
                :accept :json
                :throw-exceptions false}))

(defn lookup-promos
  [sid]
  (client/get "http://localhost:3000/api/v1/promos"
              {:query-params {:site-id sid}
               :content-type :json
               :accept :json
               :throw-exceptions false}))

(defn update-promo
  [promo-id value]
  (client/put (str "http://localhost:3000/api/v1/promos/" promo-id)
              {:body (json/write-str value)
               :content-type :json
               :accept :json
               :throw-exceptions false}))

(defn show-promo
  [sid promo-id]
  (client/get (str "http://localhost:3000/api/v1/promos/" promo-id)
              {:query-params {:site-id sid}
               :accept :json
               :throw-exceptions false}))

(defn lookup-promo-by-code
  [code sid]
  (client/get (str "http://localhost:3000/api/v1/promos/query/" code)
              {:query-params {:site-id sid}
               :content-type :json
               :accept :json
               :throw-exceptions false}))

(defn validate-promo
  [code sid body sig]
  (client/post (str "http://localhost:3000/api/v1/promos/validation/" code)
               {:body body
                :headers {:promotably-auth sig}
                :content-type :json
                :accept :json
                :throw-exceptions false}))

