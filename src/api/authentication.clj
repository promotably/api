(ns api.authentication
  (:require [api.entities :refer [users accounts]]
            [api.lib.crypto :as cr]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [org.httpkit.client :as http]
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

(defn- auth-response
  [request]
  (let [auth-token (str (UUID/randomUUID))]
    {:status 200
     :cookies (merge (:cookies request) {"__atoken" {:value auth-token}})
     :session (assoc (:session request) :auth-token auth-token)}))

(defn- authenticate-native
  [request]
  (let [{:keys [username password]} (:body-params request)
        db-user (first (select users (where {:username username})))
        encrypted-pw (:password db-user)
        pw-salt (:password_salt db-user)]
    (if (cr/verify-password password pw-salt encrypted-pw)
      (auth-response request)
      {:status 401})))

(defn- provider
  [request]
  (cond
   (:facebook-auth-token request) :facebook
   (:google-code request) :gplus
   :else :default))

(defmulti authenticate
  (fn [request]
    (provider request)))

(defmethod authenticate :facebook
  [{:keys [body-params]} :as request]
  (let [{:keys [facebook-auth-token facebook-app-token facebook-user-id]} body-params
        fb-auth-uri (format "https://graph.facebook.com/debug_token?input_token=%s&access_token=%s" facebook-auth-token facebook-app-token)
        resp @(http/get fb-auth-uri)
        body (json/read-str (:body resp) :key-fn keyword)
        fb-app-id (first (str/split facebook-app-token "|"))]
    (if (and (= 200 (:status resp))
             (= facebook-user-id (:user_id body))
             (= fb-app-id (:app_id body)))
      (auth-response request)
      {:status 401})))

(defmethod authenticate :gplus
  [{:keys [body-params]} :as request]
  (let [{:keys [google-code google-access-token google-id-token]} body-params
        gplus-auth-uri (format "https://www.googleapis.com/oauth2/v1/tokeninfo?access_token=%s" google-code)
        resp @(http/get gplus-auth-uri)
        body (json/read-str (:body resp) :key-fn keyword)
        {:keys [access_token id_token]} body]
    (if (and (= 200 (:status resp))
             (= google-access-token access_token)
             (= google-id-token id_token))
      (auth-response request)
      {:status 401})))

(defmethod authenticate :default
  [request]
  (authenticate-native request))
