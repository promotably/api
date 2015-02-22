(ns api.config
  (:require [com.stuartsierra.component :as component]))

;; File-based config data
(def configfile-data {})

;; Static name of the ns session cookie
(def session-cookie-name "promotably-session")

;; Static name of the authorization cookie
(def auth-cookie-name "promotably-auth")

;; Default build stuff
(def default-build-bucket "promotably-build-artifacts")
(def default-index-file "jenkins/dashboard/latest/index.html")

;; Setup info for logging
(def base-log-config
  (if-not (empty? (System/getProperty "catalina.base"))
    {:name "catalina"
     :level :info
     :out (org.apache.log4j.FileAppender.
           (org.apache.log4j.PatternLayout.
            "%d{HH:mm:ss} %-5p %22.22t %-22.22c{2} %m%n")
           (str (. System getProperty "catalina.base")
                "/logs/tail_catalina.log")
           true)}
    {:name "console"
     :level :info
     :out (org.apache.log4j.ConsoleAppender.
           (org.apache.log4j.PatternLayout.
            "%d{HH:mm:ss} %-5p %22.22t %-22.22c{2} %m%n"))}))

(defn- get-config-value
  [key & [default]]
  (or (System/getenv key)
      (System/getProperty key)
      (get configfile-data key default)))

(defn- get-dashboard-config
  "Checks environment variables for dashboard config settings. These
  should always be present on environments deployed to AWS"
  []
  (let [bucket (get-config-value "ARTIFACT_BUCKET")
        path (get-config-value "DASHBOARD_INDEX_PATH")]
    {:artifact-bucket (or bucket default-build-bucket)
     :index-filename (or path default-index-file)}))

(defn- get-kinesis-config
  "Checks environment variables for kinesis config settings. These
  should always be present on environments deployed to AWS"
  []
  (let [event-stream-name (get-config-value "KINESIS_A")
        promo-stream-name (get-config-value "KINESIS_B")]
    {:promo-stream-name promo-stream-name
     :event-stream-name event-stream-name}))

(defn- get-database-config
  "Checks environment variables for database config settings. These
  should always be present on environments deployed to AWS"
  []
  (let [db-host (get-config-value "RDS_HOST")
        db-name (get-config-value "RDS_DB_NAME")
        db-port (if-let [p (get-config-value "RDS_PORT")] (read-string p))
        db-user (get-config-value "RDS_USER")
        db-pwd (get-config-value "RDS_PW")]
    {:db db-name
     :user db-user
     :password db-pwd
     :host db-host
     :port db-port
     :make-pool? true}))

(defn- get-redis-config
  "Checks environment variables for redis config settings. These
  should always be present on environments deployed to AWS"
  []
  (let [host (get-config-value "REDIS_HOST")
        port (if-let [p (get-config-value "REDIS_PORT")] (read-string p))]
    {:host host :port port}))

(defn- auth-token-config
  []
  {:facebook {:app-id "1523186741303436"
              :app-secret "14056f787e48ed9f20305c98239f6835"}
   :google {:client-id "396195012878-16478fi00kv3aand6b6qqrp1mn5t4h5s.apps.googleusercontent.com"
            :client-secret "f40o9PHz-AQvpsSYHYNXC1y8"}
   :api {:api-secret "8a98e8e073038e03d69f8c809e9a9ab97219d5e9a2b3eec"}})

(defn app-config
  []
  {:dev        {:database {:db "promotably_dev"
                           :user "p_user"
                           :password "pr0m0"
                           :host "localhost"
                           :port 5432
                           :make-pool? true}
                :redis {:host "localhost" :port 6379}
                :kinesis {:aws-credential-profile "promotably"
                          :promo-stream-name "dev-PromoStream"
                          :event-stream-name "dev-PromotablyAPIEvents"}
                :dashboard (get-dashboard-config)
                :logging base-log-config
                :session-length-in-seconds (* 60 60 2)
                :bucket-assignment-length-in-seconds (* 60 60 24 120)
                :auth-token-config (auth-token-config)
                :env :dev}
   :test       {:database {:db "promotably_test"
                           :user "p_user"
                           :password "pr0m0"
                           :host "localhost"
                           :port 5432
                           :make-pool? true}
                :redis {:host "localhost" :port 6379}
                :kinesis  {:aws-credential-profile "promotably"
                           :promo-stream-name "dev-PromoStream"
                           :event-stream-name "dev-PromotablyAPIEvents"}
                :dashboard (get-dashboard-config)
                :logging base-log-config
                :session-length-in-seconds (* 60 60 2)
                :bucket-assignment-length-in-seconds (* 60 60 24 120)
                :auth-token-config (auth-token-config)
                :env :test}
   :staging    {:database (get-database-config)
                :kinesis (get-kinesis-config)
                :redis (get-redis-config)
                :dashboard (get-dashboard-config)
                :logging base-log-config
                :session-length-in-seconds (* 60 60 2)
                :bucket-assignment-length-in-seconds (* 60 60 24 120)
                :auth-token-config (auth-token-config)
                :env :staging}
   :integration {:database (get-database-config)
                 :test-topic (or (get-config-value "TEST_RESULTS_SNS_TOPIC_NAME")
                                 "api-integration-test")
                 :redis (get-redis-config)
                 :kinesis (get-kinesis-config)
                 :dashboard (get-dashboard-config)
                 :logging base-log-config
                 :session-length-in-seconds (* 60 60 2)
                :bucket-assignment-length-in-seconds (* 60 60 24 120)
                 :auth-token-config (auth-token-config)
                 :env :integration}
   :production {:database (get-database-config)
                :redis (get-redis-config)
                :kinesis (get-kinesis-config)
                :dashboard (get-dashboard-config)
                :logging base-log-config
                :session-length-in-seconds (* 60 60 2)
                :bucket-assignment-length-in-seconds (* 60 60 24 120)
                :auth-token-config (auth-token-config)
                :env :production}})

(defn lookup
  []
  (let [sys-env (keyword (get-config-value "ENV" "dev"))]
    (sys-env (app-config))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; System component
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Config [config-file]
  component/Lifecycle
  (start [component]
    (when config-file
      (let [data (-> config-file slurp read-string)]
        (alter-var-root #'configfile-data (constantly data))))
    (let [m (lookup)]
      (if ((:env m) #{:production :integration})
        (alter-var-root #'*warn-on-reflection* (constantly false))
        (alter-var-root #'*warn-on-reflection* (constantly true)))
      (merge component m)))
  (stop
    [component]
    component))
