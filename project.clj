(defproject api "version placeholder"
  :description "Promotably API server"
  ;; :url "http://example.com/FIXME"
  ;; :license {:name "Eclipse Public License"
  ;;           :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev {:dependencies [[midje "1.6.3"]]
                   :plugins [[drift "1.5.2"]
                             [lein-ring "0.8.10"]
                             [lein-beanstalk "0.2.7"]]}}
  :global-vars {*warn-on-reflection* true}
  :plugins [[org.clojars.cvillecsteele/lein-git-version "1.0.1"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.0"]
                 [org.clojure/data.json "0.2.5"]
                 [log4j/log4j "1.2.17"]
                 [org.postgresql/postgresql "9.3-1100-jdbc4"]
                 [org.clojars.cvillecsteele/ring-permacookie-middleware "1.0.0-SNAPSHOT"]
                 [korma "0.3.2"]
                 [clj-logging-config "1.9.10"]
                 [ring/ring-core "1.3.0"]
                 [compojure "1.1.8"]
                 [prismatic/schema "0.2.4"]
                 [clj-time "0.7.0"]
                 [clojure.joda-time "0.1.0"]
                 [com.cemerick/friend "0.2.1"]
                 [cider/cider-nrepl "0.7.0-SNAPSHOT"]
                 [org.clojure/tools.nrepl "0.2.3"]]
  :resource-paths ["resources"]
  :jvm-opts ["-Xmx1g" "-server" "-XX:+UseParallelGC" "-XX:+UseParallelOldGC"]
  :ring {:handler api.core/current-app
         :init api.core/init-app
         :destroy api.core/shutdown-app
         :auto-reload? false
         :reload-paths "src"}
  :aws {:beanstalk {:environments [{:name "promotably-api-staging"
                                    :cname-prefix "promotably-api-staging"
                                    :env {"ENV" "staging"}}]
                    :s3-bucket "lein-beanstalk.promotably-api"}})
