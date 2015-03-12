(ns api.cloudwatch
  (:require
   [api.system :refer [current-system]]
   [amazonica.aws.cloudwatch :refer [put-metric-data]]))

(defn put-metric
  [metric-name & [{:keys [value unit config dimensions] :or {dimensions []}}]]
  (let [unit (or unit "Count")
        config (or config (-> current-system :config))
        value (or value 1)
        e (or (:env config) "unknown")]
    (put-metric-data :namespace (str "api-" (name e))
                     :metric-data [{:unit unit
                                    :value value
                                    :metric-name metric-name
                                    :dimensions dimensions}])))
