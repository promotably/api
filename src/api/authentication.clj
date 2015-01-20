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

(defn- authenticate-social
  [request]
  (let [{:keys [username facebook-user-id google-id-token]} (:body-params request)
        user-social-id (or facebook-user-id google-id-token)]
    ;;TODO: How do we authenticate a previous social login?
    (if (and username user-social-id)
      (let [db-user (first (select users (where {:username username})))
            db-user-social-id (:user-social-id db-user)]
        (if (= user-social-id db-user-social-id)
          (auth-response request)
          {:status 401}))
      {:status 401})))

(defn authenticate
  [request]
  (if (get-in request [:body-params :password])
    (authenticate-native request)
    (authenticate-social request)))

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
  [request social-config]
  (let [login-provider (provider request)
        validator (login-provider provider-validators)
        social-token-map (login-provider social-config)]
    (fn [] (validator social-token-map))))

(defn- remove-token-fields
  [body-params]
  (dissoc body-params :google-code :google-access-token :google-id-token :facebook-app-token :facebook-auth-token :facebook-user-id))

(defn- add-social-data
  [request social-config]
  (let [validator (get-provider-validator request social-config)
        body-params (:body-params request)]
    (when validator
      (remove-token-fields (merge body-params (validator))))))

(defn validate-and-create-user
  [request social-config create-user-fn]
  (if (is-social? request)
    (if-let [body-params-with-social (add-social-data request)]
      (let [create-resp (create-user-fn (assoc request :body-params body-params-with-social))]
        (merge (auth-response create-resp) create-resp))
      {:status 401})
    (let [create-resp (create-user-fn request)]
      (merge (auth-response create-resp) create-resp))))
