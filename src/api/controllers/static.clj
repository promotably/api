(ns api.controllers.static
  (:require
   [clj-time.core :refer [before? after? now] :as t]
   [api.system :refer [current-system]]
   [api.lib.util :as util]
   [api.authentication :as auth]
   [api.models.static :refer [fetch-static]]))

(defonce cached-index (atom {:cached-at nil :content nil}))
(defonce cached-register (atom {:cached-at nil :content nil}))
(defonce cached-login (atom {:cached-at nil :content nil}))

(defn serve-cached-static
  [{:keys [cloudwatch-recorder] :as req} profile-name bucket filename cached]
  ;; if it's old, refresh it, but still return current copy
  (let [expires (t/plus (now) (t/minutes 5))]
    (if (or (nil? (:content @cached))
            (after? (:cached-at @cached) expires))
      (future (fetch-static cloudwatch-recorder profile-name bucket filename cached))))
  (if (:content @cached)
    (:content @cached)
    {:status 404}))

(defn serve-cached-index
  [{:keys [cloudwatch-recorder] :as req}]
  (cloudwatch-recorder "serve-index" 1 :Count)
  (let [c (-> current-system :config)
        api-secret (util/get-api-secret)]
    (if (auth/authorized? req api-secret)
      (serve-cached-static req
                           (-> c :kinesis :aws-credential-profile)
                           (-> c :dashboard :artifact-bucket)
                           (-> c :dashboard :index-filename)
                           cached-index)
      {:status 303
       :headers {"Location" "/login"}})))

(defn serve-cached-register
  [{:keys [cloudwatch-recorder] :as req}]
  (cloudwatch-recorder "serve-register" 1 :Count)
  (let [c (-> current-system :config)
        api-secret (util/get-api-secret)]
    (if-not (auth/authorized? req api-secret)
      (serve-cached-static req
                           (-> c :kinesis :aws-credential-profile)
                           (-> c :dashboard :artifact-bucket)
                           (-> c :dashboard :register-filename)
                           cached-register)
      {:status 303
       :headers {"Location" "/"}})))

(defn serve-cached-login
  [{:keys [cloudwatch-recorder] :as req}]
  (cloudwatch-recorder "serve-login" 1 :Count)
  (let [c (-> current-system :config)
        api-secret (util/get-api-secret)]
    (if-not (auth/authorized? req api-secret)
      (serve-cached-static req
                           (-> c :kinesis :aws-credential-profile)
                           (-> c :dashboard :artifact-bucket)
                           (-> c :dashboard :login-filename)
                           cached-login)
      {:status 303
       :headers {"Location" "/"}})))

