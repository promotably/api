(ns api.core
  (:gen-class)
  (:require [com.stuartsierra.component :as c]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :refer [parse-opts]]
            [api.components :as components]
            [api.route :as route]
            [clojure.string :as str]
            [clojure.test :as ct]
            [midje.config]
            [amazonica.aws.sns :refer :all]
            midje.emission.colorize
            api.version
            [midje.repl :refer [load-facts]]))

(defn init [options]
  (alter-var-root #'route/current-system (constantly (components/system options))))

(defn start []
  (alter-var-root #'route/current-system c/start))

(defn stop []
  (alter-var-root #'route/current-system #(when % (c/stop %))))

(defn go [options]
  (init options)
  (start))

(defn reset [options]
  (stop)
  (go options))

(defn run-integration-tests
  []
  (let [result (atom nil)]
    (midje.config/with-augmented-config {:colorize "FALSE"}
      (midje.emission.colorize/init!)
      (binding [ct/*test-out* (java.io.StringWriter.)]
        (let [test-stdout (with-out-str
                            (reset! result (load-facts 'api.integration.* :print-facts)))
              test-output (-> ^java.io.StringWriter ct/*test-out* .toString)]
          [test-output test-stdout @result])))))

(defn results-to-sns
  [topic-name output stdout result]
  (let [arn (->> (list-topics)
                 :topics
                 (map :topic-arn)
                 (filter #(re-find (re-pattern topic-name) %))
                 first)]
    (publish :topic-arn arn
             :subject "Integration Test Results"
             :message (format
                       "== TEST RESULT %s %s\n%s\n\n== TEST OUTPUT\n%s\n== TEST STDOUT\n%s\n"
                       (System/getenv "STACKNAME")
                       api.version/version
                       result
                       output
                       stdout))))

(def cli-options
  [["-p" "--port PORT" "Web server listening port" :default 3000]
   ["-r" "--repl-port PORT" "Repl / Cider listening port" :default 55555]
   ;; A non-idempotent option
   ["-v" nil "Verbosity level"
    :id :verbosity
    :default 0
    :assoc-fn (fn [m k _] (update-in m [k] inc))]
   ;; A boolean option defaulting to nil
   ["-h" "--help"]])

(defn -main
  "lein run entry point"
  [& args]
  (let [{:keys [options summary errors] :as parsed} (parse-opts args cli-options)]
    (go options)
    (if (= :integration (-> route/current-system :config :env))
      (let [[test-output test-stdout result] (run-integration-tests)]
        (results-to-sns (-> route/current-system :config :test-topic)
                        test-output test-stdout result)
        (System/exit 0)))))

(comment

  (System/setProperty "ENV" "dev")
  (System/setProperty "ENV" "localdev")
  (System/getProperty "ENV")

  (prn route/current-system)
  (go {:port 3000 :repl-port 55555})
  (stop)

)
