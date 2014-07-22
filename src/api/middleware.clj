(ns api.middleware
  (:require [clojure.tools.logging :as log]
            [clojure.repl :refer [pst]]
            [clojure.pprint :refer [pprint]]))

(defn wrap-if [handler pred wrapper & args]
  (if pred
    (apply wrapper handler args)
    handler))

(defn wrap-request-logging [handler]
  (fn [{:keys [request-method uri] :as req}]
    (let [start  (System/currentTimeMillis)
          resp   (handler req)
          finish (System/currentTimeMillis)
          total  (- finish start)]
      (log/info (format "%-6s %-4d %s (%dms)"
                        request-method
                        (:status resp)
                        uri
                        total))
      resp)))

(defn wrap-exceptions
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch clojure.lang.ExceptionInfo ex
        (println (ex-data ex))
        (when-let [exdata (ex-data ex)]
          (assoc-in (assoc-in (:response exdata) [:body] (pprint (:error exdata)))
                    [:headers "X-Error"] (.getMessage ex)))))))

(defn wrap-stacktrace
  "ring.middleware.stacktrace only catches exception, not Throwable, so we replace it here."
  [handler]
  (fn [request]
    (try (handler request)
         (catch Throwable t
           (log/error t :request request)
           {:status 500
            :headers {"Content-Type" "text/plain; charset=UTF-8"}
            :body (with-out-str
                    (binding [*err* *out*]
                      (pst t)
                      (println "\n\nREQUEST:\n")
                      (pprint request)))}))))
