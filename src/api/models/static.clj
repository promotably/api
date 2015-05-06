(ns api.models.static
  (:import [java.io ByteArrayInputStream]
           [java.util UUID]
           [com.amazonaws.auth DefaultAWSCredentialsProviderChain]
           [com.amazonaws.auth.profile ProfileCredentialsProvider])
  (:require [clojure.tools.logging :as log]
            [clj-time.core :refer [before? after? now] :as t]
            [api.lib.util :as util]))

(defn fetch-static
  [cloudwatch-recorder profile-name bucket filename cached & [filter-fn]]
  (try
    (cloudwatch-recorder "static-fetch" 1 :Count)
    (let [ff (or filter-fn slurp)
          ^com.amazonaws.auth.AWSCredentialsProvider cp
          (util/get-profile profile-name)
          _ (log/logf :info "Fetching s3://%s/%s using credentials '%s'."
                      bucket
                      filename
                      profile-name)
          resp (if profile-name
                 (amazonica.aws.s3/get-object cp
                                              :bucket-name bucket
                                              :key filename)
                 (amazonica.aws.s3/get-object bucket filename))
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

