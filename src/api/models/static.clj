(ns api.models.static
  (:import [java.io ByteArrayInputStream]
           [java.util UUID]
           [com.amazonaws.auth DefaultAWSCredentialsProviderChain]
           [com.amazonaws.auth.profile ProfileCredentialsProvider])
  (:require [clojure.tools.logging :as log]
            [clj-time.core :refer [before? after? now] :as t]
            [api.lib.util :as util]))

(defn fetch-static
  [cloudwatch-recorder aws bucket filename cached & [filter-fn]]
  (try
    (cloudwatch-recorder "static-fetch" 1 :Count)
    (let [ff (or filter-fn slurp)

          _ (log/logf :info "Fetching s3://%s/%s using credentials '%s'."
                      bucket
                      filename
                      (:credential-profile aws))
          resp (amazonica.aws.s3/get-object (:credential-provider aws)
                                            :bucket-name bucket
                                            :key filename)
          content (ff (:object-content resp))]
      (if cached
        (reset! cached {:content content :cached-at (now)})
        content))
    (catch Throwable t
      (cloudwatch-recorder "static-missing" 1 :Count)
      (log/logf :error
                "Can't fetch static file. Bucket %s, file '%s' exception %s."
                bucket
                filename
                t))))

