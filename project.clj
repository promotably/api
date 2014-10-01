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
                             [lein-midje "3.0.0"]]}}
  :global-vars {*warn-on-reflection* true}
  :plugins [[org.clojars.cvillecsteele/lein-git-version "1.0.1"]
            [cider/cider-nrepl "0.7.0-20140711.132954-36"]
            ;; [cider/cider-nrepl "0.7.0-SNAPSHOT"]
            ]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.0"]
                 [org.clojure/data.json "0.2.5"]
                 [log4j/log4j "1.2.17"]
                 [org.postgresql/postgresql "9.3-1100-jdbc4"]
                 [org.clojars.cvillecsteele/ring-permacookie-middleware "1.3.0"]
                 [korma "0.4.0"]
                 [clj-logging-config "1.9.12"]
                 [ring/ring-core "1.3.1"]
                 [compojure "1.1.9"]
                 [prismatic/schema "0.2.6"]
                 [clj-time "0.8.0"]
                 [clojure.joda-time "0.2.0"]
                 [org.clojure/tools.nrepl "0.2.6"]]
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
