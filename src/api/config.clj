(ns api.config)

;; Static name of the ns session cookie
(def session-cookie-name
  "promotably-session")

;; Static name of the authorization cookie
(def auth-cookie-name
  "promotably-auth")

(def app-config
  {:dev        {:database {:db "promotably_dev"
                           :user "p_user"
                           :password "pr0m0"
                           :host "localhost"
                           :port 5432
                           :make-pool? true}
                :kinesis {}
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
                :kinesis {}
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
   :staging    {:database {:db "promotably_staging"
                           :user "promoStaging"
                           :password "z1H0rJxmF3qS"
                           :host "rds.staging.promotably.com"
                           :port 5432
                           :make-pool? true}
                :kinesis {}
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
   :production {:database {}
                :kinesis {}
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
