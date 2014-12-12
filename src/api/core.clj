(ns api.core
  (:import [org.apache.commons.daemon Daemon DaemonContext])
  (:gen-class :implements [org.apache.commons.daemon.Daemon])
  (:require [com.stuartsierra.component :as c]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :refer [parse-opts]]
            [api.components :as components]
            [api.route :as route]
            [clojure.string :as str]
            [clojure.test :as ct]
            api.version))

(defn init [options]
  (alter-var-root #'api.system/current-system (constantly (components/system options))))

(defn start []
  (alter-var-root #'api.system/current-system c/start))

(defn stop []
  (alter-var-root #'api.system/current-system #(when % (c/stop %) nil)))

(defn go [options]
  (init options)
  (start))

(defn reset [options]
  (stop)
  (go options))

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


;; Daemon implementation

(def daemon-args (atom nil))

(defn -init [this ^DaemonContext context]
  (reset! daemon-args (.getArguments context)))

(defn -start [this]
  (let [{:keys [options summary errors] :as parsed} (parse-opts
                                                     @daemon-args
                                                     cli-options)]
    (go options)))

(defn -stop [this]
    (stop))


;; Main entry point

(defn -main
  "lein run entry point"
  [& args]
  (let [{:keys [options summary errors] :as parsed} (parse-opts args cli-options)]
    (go options)))


;; For REPL development

(comment

  (System/setProperty "ENV" "dev")
  (System/setProperty "ENV" "localdev")
  (System/getProperty "ENV")

  (prn api.system/current-system)
  (-> api.system/current-system :redis prn)
  (go {:port 3000 :repl-port 55555})
  (stop)

)
