(defproject api "version placeholder"
  :description "Promotably API server"
  ;; :url "http://example.com/FIXME"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[midje "1.6.3"
                                   :exclusions [joda-time
                                                org.clojure/tools.macro]]]
                   :plugins [[drift "1.5.2"]
                             [lein-midje "3.0.0"]]
                   :jvm-opts ["-DKAFKA_BROKERS=localhost:9092"]}}
  :main api.core

  :global-vars {*warn-on-reflection* false}
  :plugins [[org.clojars.cvillecsteele/lein-git-version "1.0.2"]
            [org.clojars.strongh/lein-init-script "1.3.1"]
            [cider/cider-nrepl "0.8.0"]]
  :dependencies [[compojure "1.1.9" :exclusions [joda-time]]
                 [org.clojure/tools.cli "0.3.1"]
                 [amazonica "0.2.30" :exclusions [joda-time]]
                 [clj-http "0.9.2"
                  :exclusions [commons-logging
                               org.clojure/tools.reader
                               com.fasterxml.jackson.core/jackson-core]]
                 [clj-logging-config "1.9.12"]
                 [clj-time "0.8.0" :exclusions [joda-time]]
                 [clojure.joda-time "0.2.0" :exclusions [joda-time]]
                 [com.stuartsierra/component "0.2.2"]
                 [com.taoensso/carmine "2.7.0"
                  :exclusions [com.taoensso/nippy
                               org.clojure/clojure
                               org.tukaani/xz
                               org.clojure/tools.reader]]
                 [korma "0.4.0"]
                 [log4j/log4j "1.2.17"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/tools.logging "0.3.0"]
                 [org.clojure/tools.nrepl "0.2.6"]
                 [org.postgresql/postgresql "9.3-1100-jdbc4"]
                 [com.cognitect/transit-clj "0.8.259"
                  :exclusions [com.fasterxml.jackson.core/jackson-databind
                               com.fasterxml.jackson.core/jackson-core]]
                 [slingshot "0.12.1"]
                 [prismatic/schema "0.2.6"]
                 [com.stuartsierra/dependency "0.1.1"]
                 [drift "1.5.2"]
                 [joda-time/joda-time "2.5"]
                 [http-kit "2.1.18"]
                 [com.taoensso/sente "0.14.1"]
                 [compojure "1.1.9"]
                 [ring/ring-core "1.3.1" :exclusions [joda-time]]
                 [org.clojars.cvillecsteele/ring-permacookie-middleware "1.3.0"]
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
                 [org.apache.commons/commons-daemon "1.0.9"]]
  :resource-paths ["resources"]
  :jvm-opts ["-Xmx1g" "-server" "-XX:+UseParallelGC" "-XX:+UseParallelOldGC"
             ;; "–XX:+UseG1GC"
             "-DEVENT_STREAM_NAME=dev-PromotablyAPIEvents"
             "-DPROMO_STREAM_NAME=dev-PromoStream"]
  )
