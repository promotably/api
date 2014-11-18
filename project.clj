(defproject api "version placeholder"
  :description "Promotably API server"
  ;; :url "http://example.com/FIXME"
  ;; :license {:name "Eclipse Public License"
  ;;           :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev {:dependencies [[midje "1.6.3"]
                                  [javax.servlet/servlet-api "2.5"]]
                   :plugins [[drift "1.5.2"]
                             [lein-ring "0.8.10"]
                             [lein-beanstalk "0.2.7"]
                             [lein-midje "3.0.0"]]
                   :jvm-opts ["-DKAFKA_BROKERS=localhost:9092"]}}
  :global-vars {*warn-on-reflection* true}
  :plugins [[org.clojars.cvillecsteele/lein-git-version "1.0.1"]
            ;;[cider/cider-nrepl "0.7.0-20140711.132954-36"]
            [cider/cider-nrepl "0.8.0-SNAPSHOT"]]
  :dependencies [[amazonica "0.2.29"]
                 [clojure.joda-time "0.2.0"]
                 [clj-http "0.9.2"]
                 [clj-kafka "0.2.6-0.8" :exclusions [org.apache.zookeeper/zookeeper]]
                 [clj-logging-config "1.9.12"]
                 [clj-time "0.8.0"]
                 [compojure "1.1.9"]
                 [com.stuartsierra/component "0.2.2"]
                 [com.taoensso/carmine "2.7.0"]
                 [korma "0.4.0"]
                 [log4j/log4j "1.2.17"]
                 [org.clojars.cvillecsteele/ring-permacookie-middleware "1.3.0"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/data.fressian "0.2.0"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/tools.logging "0.3.0"]
                 [org.clojure/tools.nrepl "0.2.6"]
                 [org.postgresql/postgresql "9.3-1100-jdbc4"]
                 [com.cognitect/transit-clj "0.8.259"]
                 [slingshot "0.12.1"]
                 [prismatic/schema "0.2.6"]
                 [ring.middleware.jsonp "0.1.6"]
                 [ring/ring-core "1.3.1"]
                 [ring/ring-json "0.3.1"]]
  :resource-paths ["resources"]
  :jvm-opts ["-Xmx1g" "-server" "-XX:+UseParallelGC" "-XX:+UseParallelOldGC"
             "-DEVENT_STREAM_NAME=dev-PromotablyAPIEvents"
             "-DPROMO_STREAM_NAME=dev-PromoStream"]
  :ring {:handler api.system/servlet-handler
         :init api.system/init-servlet
         :destroy api.system/stop
         :auto-reload? false
         :reload-paths "src"}
  :aws {:beanstalk {:environments [{:name "promotably-api-staging"
                                    :cname-prefix "promotably-api-staging"
                                    :env {"ENV" "staging"}}]
                    :s3-bucket "lein-beanstalk.promotably-api"}})
