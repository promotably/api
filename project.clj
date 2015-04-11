(defproject api "version placeholder"
  :description "Promotably API server"
  ;; :url "http://example.com/FIXME"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[midje "1.6.3"
                                   :exclusions [joda-time
                                                org.clojure/tools.macro]]]
                   :plugins [[drift "1.5.2"]
                             [lein-midje "3.1.3"]]}}
  :main api.core
  :aliases {"test-uuid" ["run" "-m" "tasks.test-uuid"]}
  :aot [api.connection-customizer]
  :global-vars {*warn-on-reflection* false}
  :plugins [[org.clojars.cvillecsteele/lein-git-version "1.0.2"]
            [cider/cider-nrepl "0.8.2"]]
  :dependencies [[compojure "1.1.9" :exclusions [joda-time]]
                 [org.clojure/tools.cli "0.3.1"]
                 [amazonica "0.3.18" :exclusions [joda-time]]
                 [clj-http "0.9.2"
                  :exclusions [commons-logging
                               org.clojure/tools.reader
                               com.fasterxml.jackson.core/jackson-core]]
                 [clj-logging-config "1.9.12"]
                 [log4j/log4j "1.2.17"]
                 [org.slf4j/slf4j-log4j12 "1.2"]
                 [clj-time "0.8.0" :exclusions [joda-time]]
                 [clojure.joda-time "0.2.0" :exclusions [joda-time]]
                 [joda-time/joda-time "2.5"]
                 [com.stuartsierra/component "0.2.2"]
                 [com.taoensso/carmine "2.9.0"
                  :exclusions [com.taoensso/nippy
                               org.clojure/clojure
                               org.tukaani/xz
                               org.clojure/tools.reader]]
                 [korma "0.4.0"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/tools.logging "0.3.0"]
                 [org.clojure/tools.nrepl "0.2.6"]
                 [org.postgresql/postgresql "9.3-1100-jdbc4"]
                 [org.clojars.promotably/proggly "0.1.8"]
                 [com.cognitect/transit-clj "0.8.259"
                  :exclusions [com.fasterxml.jackson.core/jackson-databind
                               com.fasterxml.jackson.core/jackson-core]]
                 [slingshot "0.12.1"]
                 [clojurewerkz/scrypt "1.2.0"]
                 [org.clojure/core.match "0.3.0-alpha1" :exclusions [org.clojure/tools.reader]]
                 [prismatic/schema "0.2.6"]
                 [com.stuartsierra/dependency "0.1.1"]
                 [drift "1.5.2"]
                 [http-kit "2.1.18"]
                 [com.taoensso/sente "0.14.1"]
                 [ring/ring-core "1.3.1" :exclusions [joda-time]]
                 [org.clojars.cvillecsteele/ring-permacookie-middleware "1.4.0"]
                 [bk/ring-gzip "0.1.1"]
                 [ring.middleware.jsonp "0.1.6"]
                 [com.taoensso.forks/ring-anti-forgery "0.3.1"]
                 [ring-middleware-format "0.4.0"
                  :exclusions [com.fasterxml.jackson.core/jackson-annotations
                               joda-time
                               org.clojure/tools.reader
                               com.fasterxml.jackson.core/jackson-databind
                               org.clojure/java.classpath
                               com.fasterxml.jackson.core/jackson-core]]
                 [ring/ring-json "0.3.1"
                  :exclusions [joda-time com.fasterxml.jackson.core/jackson-core]]
                 [org.apache.commons/commons-daemon "1.0.9"]
                 [commons-codec "1.10"]
                 [net.sf.uadetector/uadetector-resources "2014.10"]
                 [net.logstash.log4j/jsonevent-layout "1.7"]
                 [crypto-random "1.2.0"]
                 [org.clojars.promotably/apollo "0.2.0"]]
  :resource-paths ["resources"]
  :jvm-opts ["-Xmx1g" "-server" "-XX:+UseParallelGC" "-XX:+UseParallelOldGC" "-XX:MaxPermSize=256m"
             ;; "–XX:+UseG1GC"
             "-DEVENT_STREAM_NAME=dev-PromotablyAPIEvents"
             "-DPROMO_STREAM_NAME=dev-PromoStream"]
  )
