(ns api.integration.helper
  (:import org.postgresql.util.PGobject
           [java.net URLEncoder])
  (:require
   [clojure.tools.logging :as log]
   [api.fixtures.basic :as base]
   [api.route :as route]
   [api.system :as system]
   [api.config :as config]
   [api.lib.crypto :as cr]
   [korma.db :as kdb]
   [korma.core :as korma]
   [api.q-fix :as qfix]
   [api.db :as db]
   [api.lib.seal :refer [hmac-sha1 url-encode]]
   [api.core :as core]
   [clj-time.format :as tf]
   [clj-time.coerce :refer (to-sql-time)]
   [clojure.java.jdbc :as jdbc]
   [clojure.walk :refer [postwalk]]
   [clj-time.core :refer (now) :as t]
   [drift.execute :as drift]
   [midje.sweet :refer :all]
   [clj-http.client :as client]
   [clojure.data.json :as json]
   [ring.adapter.jetty :refer (run-jetty)]
   [com.stuartsierra.component :as component]
   [api.components :as c]))

(def expected-db-version 20150604164644)

(def test-target (atom (java.net.URL. "http://localhost:3000")))

(defn test-target-url
  []
  (str @test-target))

(defn truncate
  []
  (doseq [t api.entities/tables-to-truncate]
    (korma/exec-raw [(str "TRUNCATE " t " CASCADE")]))
  (jdbc/with-db-transaction [spec (kdb/postgres (get-in system/current-system [:config :database]))]
    (let [conn (jdbc/get-connection spec)]
      (let [s (.prepareCall conn "SELECT add_superuser_to_users();")]
      (.execute s)))))

(defn migrate-down
  []
  (with-out-str (drift.execute/migrate 0 [])))

(defn migrate-up
  []
  (with-out-str (drift.execute/migrate Long/MAX_VALUE [])))

(defn migrate-or-truncate
  []
  (if-not (= expected-db-version (db/db-version))
    (do
      (migrate-down)
      (migrate-up)
      (when-not (= expected-db-version (db/db-version))
        (log/logf :fatal "Expected db version %s" (str expected-db-version))
        (System/exit 0)))
    (truncate)))

(defn load-fixture-set
  [fset]
  (let [config (-> system/current-system :config :database)]
    (jdbc/with-db-transaction [t-con (kdb/postgres config)]
      (qfix/load fset
                 (fn [table-name val-map]
                   (let [xformed (postwalk #(if (fn? %) (% t-con) %)
                                           val-map)
                         result (jdbc/insert! t-con table-name xformed)]
                     (-> result first :id)))))))

(defn test-user-id [] (str (:user_id (first (korma/exec-raw ["SELECT user_id from users WHERE email = 'global@promotably.com'"] :results)))))

(defn auth-cookie-token []
  (URLEncoder/encode (cr/aes-encrypt (json/write-str {:user-id (test-user-id)})
                                     (get-in system/current-system [:config :auth-token-config :api :api-secret]))))

(defn promotably-user-cookie []
  (URLEncoder/encode (json/write-str {:user-id (test-user-id)}) "utf8"))

(defn build-auth-cookie-string []
  (format "__apiauth=%s; promotably-user=%s;" (auth-cookie-token) (promotably-user-cookie)))

(defn create-promo
  [new-promo]
  (client/post (str (test-target-url) "/api/v1/promos")
               {:headers {"cookie" (build-auth-cookie-string)}
                :body (json/write-str new-promo)
                :content-type :json
                :accept :json
                :throw-exceptions false}))

(defn lookup-promos
  [sid]
  (client/get (str (test-target-url) "/api/v1/promos")
              {:headers {"cookie" (build-auth-cookie-string)}
               :query-params {:site-id sid}
               :content-type :json
               :accept :json
               :throw-exceptions false}))

(defn update-promo
  [promo-id value]
  (client/put (str (test-target-url) "/api/v1/promos/" promo-id)
              {:headers {"cookie" (build-auth-cookie-string)}
               :body (json/write-str value)
               :content-type :json
               :accept :json
               :throw-exceptions false}))

(defn show-promo
  [sid promo-id]
  (client/get (str (test-target-url) "/api/v1/promos/" promo-id)
              {:headers {"cookie" (build-auth-cookie-string)}
               :query-params {:site-id sid}
               :accept :json
               :throw-exceptions false}))

(defn lookup-promo-by-code
  [code sid]
  (client/get (str (test-target-url) "/api/v1/promos/query/" code)
              {:headers {"cookie" (build-auth-cookie-string)}
               :query-params {:site-id sid}
               :content-type :json
               :accept :json
               :throw-exceptions false}))

(defn validate-promo
  [code sid body sig]
  (client/post (str (test-target-url) "/api/v1/promos/validation/" code)
               {:body body
                :headers {:promotably-auth sig}
                :content-type :json
                :accept :json
                :throw-exceptions false
                :socket-timeout 10000
                :conn-timeout 10000}))

(defn query-promo
  [code sid body sig]
  (client/get (str (test-target-url) "/api/v1/promos/query/" code)
              {:body body
               :headers {:promotably-auth sig}
               :content-type :json
               :accept :json
               :throw-exceptions false
               :socket-timeout 10000
               :conn-timeout 10000}))

(defn compute-sig-hash
  [host verb path body site-id api-secret]
  (let [body-hash (if body (hmac-sha1 (.getBytes api-secret)
                                      (.getBytes body)))
        time-val (tf/unparse (tf/formatters :basic-date-time-no-ms)
                             (t/now))
        sig-str (hmac-sha1 (.getBytes api-secret)
                           (.getBytes (apply str
                                             (str site-id) "\n"
                                             api-secret "\n"
                                             host "\n"
                                             verb "\n"
                                             path "\n"
                                             time-val "\n"
                                             body-hash "\n"
                                             "" "\n"
                                             "" "\n")))]
    (str "hmac-sha1///" time-val "/" sig-str)))


(defn targeted-integration-test-system
  []
  (component/system-map
   :config       (component/using (config/map->Config {}) [])
   :logging      (component/using (c/map->LoggingComponent {}) [:config])
   :database     (component/using (c/map->DatabaseComponent {}) [:config :logging])))

(defn init!
  []
  (let [target-url (or (System/getenv "TARGET_URL")
                       (System/getProperty "TARGET_URL"))]
    (if-not (or (nil? target-url))
      (do (reset! test-target (java.net.URL. target-url))
          (when (nil? api.system/current-system)
            (alter-var-root #'api.system/current-system (constantly (targeted-integration-test-system)))
            (alter-var-root #'api.system/current-system component/start)))
      (when (nil? system/current-system)
        (core/go {:port 3000 :repl-port 55555}))))
  (migrate-or-truncate))
