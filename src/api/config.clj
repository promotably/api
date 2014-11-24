(ns api.config)

;; Static name of the ns session cookie
(def session-cookie-name
  "promotably-session")

;; Static name of the authorization cookie
(def auth-cookie-name
  "promotably-auth")

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
        db-port (System/getenv "RDS_PORT")
        db-user (System/getenv "RDS_USER")
        db-pwd (System/getenv "RDS_PW")]
    {:db db-name
     :user db-user
     :password db-pwd
     :host db-host
     :port db-port
     :make-pool? true}))

(def app-config
  {:dev        {:database {:db "promotably_dev"
                           :user "p_user"
                           :password "pr0m0"
                           :host "localhost"
                           :port 5432
                           :make-pool? true}
                :kinesis {:aws-credential-profile "promotably"
                          :promo-stream-name "dev-PromoStream"
                          :event-stream-name "dev-PromotablyAPIEvents"}
                :logging (if-not (empty? (System/getProperty "catalina.base"))
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
                                   "%d{HH:mm:ss} %-5p %22.22t %-22.22c{2} %m%n"))})
                :env :dev}
   :test       {:database {}
                :kinesis  {:aws-credential-profile "promotably"
                          :promo-stream-name "dev-PromoStream"
                          :event-stream-name "dev-PromotablyAPIEvents"}
                :logging (if-not (empty? (System/getProperty "catalina.base"))
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
                                   "%d{HH:mm:ss} %-5p %22.22t %-22.22c{2} %m%n"))})
                :env :test}
   :staging    {:database (get-database-config)
                :kinesis (get-kinesis-config)
                :logging (if-not (empty? (System/getProperty "catalina.base"))
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
                                   "%d{HH:mm:ss} %-5p %22.22t %-22.22c{2} %m%n"))})
                :env :staging}
   :production {:database (get-database-config)
                :kinesis (get-kinesis-config)
                :logging (if-not (empty? (System/getProperty "catalina.base"))
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
                                   "%d{HH:mm:ss} %-5p %22.22t %-22.22c{2} %m%n"))})
                :env :production}})

(defn lookup
  []
  (let [sys-env (keyword (or (System/getProperty "ENV")
                             (System/getenv "ENV")
                             "dev"))]
    (sys-env app-config)))
