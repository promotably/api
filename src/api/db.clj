(ns api.db
  (:require [korma.core :refer :all]
            [korma.db :as kdb]
            [joda-time :refer [date-time to-millis-from-epoch]]
            [clojure.java.jdbc :as jdbc]))

;; TODO: use api.env instead of this
(defonce $env (atom nil))
(defonce db-environment-configs (atom nil))
(defonce $db-config (atom nil))

(defn init!
  "Initialize db subsystem."
  [& [env]]
  ;; TODO: use api.env instead of this
  (reset! $env (or env
                   (System/getenv "ENV")
                   (System/getProperty "ENV")
                   "dev"))
  (reset! db-environment-configs {"dev" {:db "promotably_dev"
                                         :user "p_user"
                                         :password "pr0m0"
                                         :host "localhost"
                                         :port 5432
                                         :make-pool? true}
                                  "test" {}
                                  "staging" {:db "promotably_staging"
                                             :user "promoStaging"
                                             :password "z1H0rJxmF3qS"
                                             :host "rds.staging.promotably.com"
                                             :port 5432
                                             :make-pool? true}})
  (when-not (= "test" @$env)
    (reset! $db-config (kdb/postgres (get @db-environment-configs @$env)))
    (kdb/defdb $the-db @$db-config)))

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
