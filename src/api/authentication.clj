(ns api.authentication
  (:require [api.entities :refer [users accounts]]
            [api.lib.crypto :as cr]
            [clojure.string :as str]
            [korma.core :refer :all])
  (:import [java.util UUID]))

(defn authorized?
  [handler request]
  (when-let [cookie-auth-token (-> request :cookies "__atoken" :value)]
    (when-let [session-auth-token (-> request :session :auth-token)]
      (when (= cookie-auth-token session-auth-token)
        (handler request)))))

(defn wrap-authorized
  [handler]
  ;; TODO: add role based authorization
  (fn [request]
    (or (authorized? handler request)
        {:status 301
         :headers {"Location" "/login"}})))

(defn authenticate
  [request]
  (let [{:keys [username password]} (:body-params request)
        db-user (first (select users (where {:username username})))
        encrypted-pw (:password db-user)
        pw-salt (:password_salt db-user)]
    (if (cr/verify-password password pw-salt encrypted-pw)
      (let [auth-token (str (UUID/randomUUID))]
        ;; TODO make auth token cookie valid only on https
        {:status 200
         :cookies (merge (:cookies request) {"__atoken" {:value auth-token}})
         :session (assoc (:session request) :auth-token auth-token)})
      {:status 401})))
