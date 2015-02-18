(ns api.db
  (:require [korma.core :refer :all]
            [korma.db :as kdb]
            [joda-time :refer [date-time to-millis-from-epoch]]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
            [api.config :as config])
  (:import org.postgresql.util.PGobject))

(defonce $db-config (atom nil))

(defn- init!
  "Initialize db subsystem."
  []
  (reset! $db-config (kdb/postgres (-> (config/lookup) :database)))
  (kdb/defdb $the-db @$db-config))

(defn db-version
  "Gets the current version of the database"
  []
  (if-not @$db-config (init!))
  (let [v (:version
           (first
            (jdbc/query
             @$db-config
             ["SELECT version FROM migrations ORDER BY version DESC LIMIT 1"])))]
    (Long/parseLong (or v "0"))))

(defn update-db-version
  "Updates the current version of the database"
  [version]
  (if-not @$db-config (init!))
  (jdbc/with-db-transaction [t-con @$db-config]
    (jdbc/delete! t-con :migrations ["version IS NOT NULL"])
    (jdbc/insert! t-con :migrations {:version version})))

(defn value-to-json-pgobject [value]
  (doto (PGobject.)
    (.setType "json")
    (.setValue (json/write-str value))))

(extend-protocol jdbc/ISQLValue
  clojure.lang.IPersistentMap
  (sql-value [value] (value-to-json-pgobject value))

  clojure.lang.IPersistentVector
  (sql-value [value] (value-to-json-pgobject value)))

(extend-protocol jdbc/IResultSetReadColumn
  PGobject
  (result-set-read-column [pgobj metadata idx]
    (let [type  (.getType pgobj)
          value (.getValue pgobj)]
      (case type
        "json" (json/read-str value :key-fn keyword)
                :else value))))
