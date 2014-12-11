(ns api.cloudwatch
  (:require
   [api.system :refer [current-system]]
   [amazonica.aws.cloudwatch :refer [put-metric-data]]))

(defn put-metric
  [metric-name & [{:keys [value unit config]}]]
  (let [unit (or unit "Count")
        config (or config (-> current-system :config))
        value (or value 1)]
    (put-metric-data :namespace (str "api-" (name (:env config)))
                     :metric-data [{:unit unit
                                    :value value
                                    :metric-name metric-name}])))
