(ns api.lib.util
  (:import [com.amazonaws.auth DefaultAWSCredentialsProviderChain]
           [com.amazonaws.auth.profile ProfileCredentialsProvider])
  (:require
   [api.system :refer [current-system]]))

(defn get-api-secret
  []
  (get-in current-system [:config :auth-token-config :api :api-secret]))

(defn get-profile
  [profile-name]
  (if profile-name
    (ProfileCredentialsProvider. profile-name)
    (DefaultAWSCredentialsProviderChain.)))

