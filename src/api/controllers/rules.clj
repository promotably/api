(ns api.controllers.rules
  (:require [api.models.rule :refer [new-rule! InboundTimeFrameRule]]
            [api.lib.coercion-helper :refer [custom-matcher]]
            [clojure.tools.logging :as log]
            [schema.coerce :as c]))

(defmulti coerce-new-rule-params :type)

(defmethod coerce-new-rule-params "time-frame"
  [params]
  (let [coercer (c/coercer InboundTimeFrameRule
                           (c/first-matcher [custom-matcher
                                             c/string-coercion-matcher]))
        params-ready-for-coercion (conj params
                                        {:time-frames (vals (:time-frames params))})]
    (coercer params-ready-for-coercion)))

(defn create-new-rule!
  [{:keys [params] :as request}]
  (log/trace "Incoming create-rule request")
  (let [coerced-params (coerce-new-rule-params params)]
    (try
      (new-rule! coerced-params)
      (catch clojure.lang.ExceptionInfo ex
        (log/error ex "Exception in create-new-rule")))))

(defn show-rule
  [request]
  (log/trace "Incoming show-rule request")
  (log/debug request)
  (try
    (catch clojure.lang.ExceptionInfo ex
      (log/error ex "Exception in show-rule"))))
