(ns api.authentication
  (:require [api.entities :refer [users accounts]]
            [api.lib.crypto :as cr]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [org.httpkit.client :as http]
            [korma.core :refer :all])
  (:import [java.util UUID]))

(defn- generate-user-auth-token
  [user-id api-secret]
  (let [token-data (json/write-str {:user-id user-id})]
    (cr/aes-encrypt token-data api-secret)))

(defn- get-user-id-from-auth-token
  [auth-token api-secret]
  (let [decrypted-json (cr/aes-decrypt auth-token api-secret)]
    (:user-id (json/read-str decrypted-json :key-fn keyword))))

(defn- authorized?
  [request api-secret]
  (let [cookie-auth-token (-> request :cookies "promotably-auth" :value)
        user-data-cookie (-> request :cookies "promotably-user" :value (json/read-str :key-fn keyword))
        current-user-id (:user-id user-data-cookie)
        auth-cookie-user-id (get-user-id-from-auth-token cookie-auth-token api-secret)]
    (= current-user-id auth-cookie-user-id)))

(defn wrap-authorized
  [handler api-secret]
  ;; TODO: add role based authorization
  (fn [request]
    (if (authorized? request api-secret)
      (handler request)
      {:status 301
       :headers {"Location" "/login"}})))

(defn- auth-response
  [request api-secret user-id & {:keys [remember?]}]
  (let [expiry (if remember?
                 (tf/unparse (tf/formatters :basic-date-time)
                             (t/plus (t/now) (t/years 10)))
                 "Session")
        auth-token (generate-user-auth-token user-id api-secret)]
    {:status 201
     :cookies (merge (:cookies request) {"promotably-auth" {:value auth-token
                                                            :http-only true
                                                            :expires expiry}
                                         "promotably-user" {:value (json/write-str {:user-id user-id})
                                                            :expires expiry}})
     :session (assoc (:session request) :auth-token auth-token)}))

(defn is-social?
  [{:keys [body-params]}]
  (or (:google-code body-params)
      (:facebook-auth-token body-params)))

(defn- get-google-dd
  []
  (-> @(http/get "https://accounts.google.com/.well-known/openid-configuration")
      :body
      (json/read-str :key-fn keyword)))

(def google-dd (memoize get-google-dd))

(defn- get-google-token-endpoint
  []
  (:token_endpoint (google-dd)))

(defn provider
  [{:keys [body-params]}]
  (cond
   (:facebook-auth-token body-params) :facebook
   (:google-code body-params) :google
   :else nil))

(defn- validate-facebook
  [{:keys [body-params] :as request} social-token-map]
  (let [{:keys [facebook-auth-token facebook-user-id]} body-params
        {:keys [app-id app-secret]} social-token-map
        facebook-app-token (format "%s|%s" app-id app-secret)
        fb-auth-uri (format "https://graph.facebook.com/debug_token?input_token=%s&access_token=%s" facebook-auth-token facebook-app-token)
        resp @(http/get fb-auth-uri)
        body (json/read-str (:body resp) :key-fn keyword)
        {:keys [user_id app_id]} body]
    (when (and (= 200 (:status resp))
               (= facebook-user-id user_id)
               (= app-id app_id))
      [:user-social-id facebook-user-id])))

(defn- validate-google
  [{:keys [body-params] :as request} social-token-map]
  (let [{:keys [google-code google-access-token google-id-token]} body-params
        {:keys [client-id client-secret]} social-token-map
        gplus-token-endpoint (get-google-token-endpoint)
        resp @(http/post gplus-token-endpoint
                         {:method :post
                          :headers {"Content-Type" "application/x-www-form-urlencoded"}
                          :form-params {"code" google-code
                                        "client_id" client-id
                                        "client_secret" client-secret
                                        "redirect_uri" "postmessage"
                                        "grant_type" "authorization_code"}})
        body (json/read-str (:body resp) :key-fn keyword)
        {:keys [access_token id_token]} body]
    (when (and (= 200 (:status resp))
               (= google-access-token access_token))
      [:user-social-id google-id-token])))

(def provider-validators
  {:facebook validate-facebook
   :google validate-google})

(defn- get-provider-validator
  [request auth-config]
  (let [login-provider (provider request)
        validator (login-provider provider-validators)
        social-token-map (login-provider auth-config)]
    (fn [] (validator social-token-map))))

(defn- remove-token-fields
  [body-params]
  (dissoc body-params :google-code :google-access-token :google-id-token :facebook-app-token :facebook-auth-token :facebook-user-id))

(defn- add-social-data
  [request auth-config]
  (let [validator (get-provider-validator request auth-config)
        body-params (:body-params request)]
    (when validator
      (remove-token-fields (merge body-params (validator))))))

(defn validate-and-create-user
  [request auth-config create-user-fn]
  (if (is-social? request)
    (if-let [body-params-with-social (add-social-data request auth-config)]
      (let [create-resp (create-user-fn (assoc request :body-params body-params-with-social))
            user-id (str (get-in create-resp [:body :user :user-id]))
            api-secret (get-in auth-config [:api :api-secret])]
        (merge (auth-response create-resp api-secret user-id) create-resp))
      {:status 401})
    (let [create-resp (create-user-fn request)
          user-id (str (get-in create-resp [:body :user :user-id]))
          api-secret (get-in auth-config [:api :api-secret])]
      (merge (auth-response create-resp api-secret user-id) create-resp))))

(defn- authenticate-native
  [request auth-config]
  (let [{:keys [email password remember]} (:body-params request)
        db-user (first (select users (where {:email email})))
        db-user-id (str (:user_id db-user))
        encrypted-pw (:password db-user)
        pw-salt (:password_salt db-user)
        api-secret (get-in auth-config [:api :api-secret])]
    (when (cr/verify-password password pw-salt encrypted-pw)
      (auth-response request api-secret db-user-id :remember? remember))))

(defn- authenticate-social
  [request auth-config]
  (let [{:keys [email]} (:body-params request)
        validator (get-provider-validator request auth-config)]
    (when validator
      (when-let [user-social-id (last (validator))]
        (let [db-user (first (select users (where {:email email})))
              db-user-social-id (:user_social_id db-user)
              db-user-id (str (:user_id db-user))
              api-secret (get-in auth-config [:api :api-secret])]
          (when (= user-social-id db-user-social-id)
            (auth-response request api-secret db-user-id)))))))

(defn authenticate
  [{:keys [body-params] :as request} auth-config]
  (if (:password body-params)
    (or (authenticate-native request auth-config) {:status 401})
    (or (authenticate-social request auth-config) {:status 401})))
