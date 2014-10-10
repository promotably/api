(ns api.config
  (require [api.env :refer [env]]))

(def app-config
  {:dev {:kafka-producer {"metadata.broker.list" (or (System/getProperty "KAFKA_BROKERS")
                                                     "localhost:9092")
                          "producer.type" "async"}
         :postgres {:db "promotably_dev"
                    :user "p_user"
                    :password "pr0m0"
                    :host "localhost"
                    :port 5432
                    :make-pool? true}
         :cookies {:session-cookie-name "promotably-session"
                   :auth-cookie-name "promotably-auth"}}
   :test {:cookies {:session-cookie-name "promotably-session"
                    :auth-cookie-name "promotably-auth"}}
   :staging {:kafka-producer {"metadata.broker.list" (System/getProperty "KAFKA_BROKERS")
                              "producer.type" "async"}
             :postgres {:db "promotably_staging"
                        :user "promoStaging"
                        :password "z1H0rJxmF3qS"
                        :host "rds.staging.promotably.com"
                        :port 5432
                        :make-pool? true}
             :cookies {:session-cookie-name "promotably-session"
                       :auth-cookie-name "promotably-auth"}}})

(defn- lookup*
  ([]
     (lookup* @env))
  ([e]
     ((keyword e) app-config)))

(def lookup
  (memoize lookup*))
