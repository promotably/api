(ns api.config
  (:require [com.stuartsierra.component :as component]))

;; Static name of the ns session cookie
(def session-cookie-name "promotably-session")

;; Static name of the authorization cookie
(def auth-cookie-name "promotably-auth")

;; Default build stuff
(def default-build-bucket "promotably-build-artifacts")
(def default-index-file "db/latest/index.html")

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

(defn- get-kinesis-config
  "Checks environment variables for kinesis config settings. These
  should always be present on environments deployed to AWS"
  []
  (let [event-stream-name (System/getenv "KINESIS_A")
        promo-stream-name (System/getenv "KINESIS_B")]
    {:promo-stream-name promo-stream-name
     :event-stream-name event-stream-name}))

(defn- get-database-config
  "Checks environment variables for database config settings. These
  should always be present on environments deployed to AWS"
  []
  (let [db-host (System/getenv "RDS_HOST")
        db-name (System/getenv "RDS_DB_NAME")
        db-port (if-let [p (System/getenv "RDS_PORT")] (read-string p))
        db-user (System/getenv "RDS_USER")
        db-pwd (System/getenv "RDS_PW")]
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
  (let [host (System/getenv "REDIS_HOST")
        port (if-let [p (System/getenv "REDIS_PORT")] (read-string p))]
    {:host host :port port}))

(def app-config
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
                :artifact-bucket default-build-bucket
                :index-filename default-index-file
                :logging base-log-config
                :session-length-in-seconds (* 60 60 2)
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
                :artifact-bucket default-build-bucket
                :index-filename default-index-file
                :logging base-log-config
                :session-length-in-seconds (* 60 60 2)
                :env :test}
   :staging    {:database (get-database-config)
                :kinesis (get-kinesis-config)
                :redis (get-redis-config)
                :artifact-bucket default-build-bucket
                :index-filename default-index-file
                :logging base-log-config
                :session-length-in-seconds (* 60 60 2)
                :env :staging}
   :integration {:database (get-database-config)
                 :test-topic (or (System/getenv "TEST_RESULTS_SNS_TOPIC_NAME")
                                 "api-integration-test")
                 :redis (get-redis-config)
                 :kinesis (get-kinesis-config)
                 :artifact-bucket default-build-bucket
                 :index-filename default-index-file
                 :logging base-log-config
                 :session-length-in-seconds (* 60 60 2)
                 :env :integration}
   :production {:database (get-database-config)
                :redis (get-redis-config)
                :kinesis (get-kinesis-config)
                :artifact-bucket default-build-bucket
                :index-filename default-index-file
                :logging base-log-config
                :session-length-in-seconds (* 60 60 2)
                :env :production}})

(defn lookup
  []
  (let [sys-env (keyword (or (System/getProperty "ENV")
                             (System/getenv "ENV")
                             "dev"))]
    (sys-env app-config)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; System component
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord Config []
  component/Lifecycle
  (start [component]
    (let [m (lookup)]
      (if ((:env m) #{:production :integration})
        (alter-var-root #'*warn-on-reflection* (constantly false))
        (alter-var-root #'*warn-on-reflection* (constantly true)))
      (merge component m)))
  (stop
    [component]
    component))
