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
  (:import [java.util UUID]
           [java.net URLEncoder]))

(defn invalidate-auth-cookies
  [request]
  (let [expiry (tf/unparse (tf/formatters :basic-date-time)
                           (t/minus (t/now) (t/minutes 10)))]
    {:status 200
     :cookies {"__apiauth" {:value ""
                            :expires expiry
                            :path "/"}
               "promotably-user" {:value ""
                                  :expires expiry
                                  :path "/"}}}))

(defn- generate-user-auth-token
  "Generates an AES encrypted json object containing the user-id using
  the api-secret key."
  [user-id api-secret]
  (let [token-data (json/write-str {:user-id user-id})]
    (cr/aes-encrypt token-data api-secret)))

(defn- get-user-id-from-auth-token
  "Decrypts the auth-token using the api-secret key and returns the
  user-id from the json object."
  [auth-token api-secret]
  (let [decrypted-json (cr/aes-decrypt auth-token api-secret)]
    (:user-id (json/read-str decrypted-json :key-fn keyword))))

(defn authorized?
  "Given a request and the api-secret key, validates that the user-id in
  the unencrypted promotably-user cookie matches the user-id in the
  encrypted promotably-auth cookie using the api-secret key."
  [request api-secret]
  (let [cookie-auth-token (-> request :cookies (get "__apiauth") :value)
        user-data-cookie (-> request :cookies (get "promotably-user") :value)]
    (when (and cookie-auth-token user-data-cookie)
      (let [current-user-id (:user-id (json/read-str user-data-cookie :key-fn keyword))
            auth-cookie-user-id (get-user-id-from-auth-token cookie-auth-token api-secret)]
        (= current-user-id auth-cookie-user-id)))))

(defn- add-user-id-to-params
  [request]
  (let [user-id (-> request
                    :cookies
                    (get "promotably-user")
                    :value
                    (json/read-str :key-fn keyword)
                    :user-id
                    UUID/fromString)]
    (assoc request :user-id user-id)))

(defn wrap-authorized
  "Middleware component for wrapping secure routes. Validates that this
  request is authorized to access the resource."
  [handler api-secret-fn]
  ;; TODO: add role based authorization
  (fn [request]
    (if (authorized? request (api-secret-fn))
      (handler (add-user-id-to-params request))
      {:status 401})))

(defn- auth-response
  "Given a response, the api-secret key, the user-id, and (optional)
  :remember? kv pair, generates and adds an encrypted authentication
  cookie signed by the api-secret key as well as an unecrypted json
  cookie containing the user-id. Sets the expiry to either Session or 10
  years from now depending on the value of the :remember? optional
  argument. Adds a status of 200 if no other status in the response is
  present, the cookies, and the auth-token to the session component of
  the response."
  [response api-secret user-id & {:keys [remember?]}]
  (let [expiry (if remember?
                 (tf/unparse (tf/formatters :basic-date-time)
                             (t/plus (t/now) (t/years 10)))
                 "Session")
        auth-token (generate-user-auth-token user-id api-secret)]
    {:status (or (:status response) 200)
     :cookies (merge (:cookies response) {"__apiauth" {:value auth-token
                                                       :http-only true
                                                       :expires expiry
                                                       :path "/"}
                                          "promotably-user" {:value (json/write-str {:user-id user-id})
                                                             :expires expiry
                                                             :path "/"}})
     :session (assoc (:session response) :auth-token auth-token)
     :body (:body response)}))

(defn- is-social?
  "Determines in the given register or login request is for a 3rd party
  provider."
  [{:keys [body-params]}]
  (or (:google-code body-params)
      (:facebook-auth-token body-params)))

(defn- get-google-dd
  "Gets the Google Discovery Document which describes the resource uri's
  for various requests."
  []
  (-> @(http/get "https://accounts.google.com/.well-known/openid-configuration")
      :body
      (json/read-str :key-fn keyword)))

;;Memoize get-google-dd since we only need to do it once.
(def google-dd (memoize get-google-dd))

(defn- get-google-token-endpoint
  "Gets the endpoint from the Discovery Document needed to validate auth
  tokens."
  []
  (:token_endpoint (google-dd)))

(defn- provider
  "Determines the 3rd party provider for this authentication request (if
  any) by inspecting specific keys sent from the client."
  [{:keys [body-params]}]
  (cond
   (:facebook-auth-token body-params) :facebook
   (:google-code body-params) :google
   :else nil))

(defn- validate-facebook
  "Given the request and a map of api keys for authenticating facebook
  logins, validates the access token from the client, and, if valid,
  returns a vector of the form [:user-social-id 'facebook-user-id'] or
  nil if validation failed."
  [{:keys [body-params] :as request} social-token-map]
  (let [{:keys [facebook-auth-token facebook-user-id]} body-params
        {:keys [app-id app-secret]} social-token-map
        facebook-app-token (URLEncoder/encode (format "%s|%s" app-id app-secret) "utf8")
        fb-auth-uri (format "https://graph.facebook.com/debug_token?input_token=%s&access_token=%s" facebook-auth-token facebook-app-token)
        resp @(http/get fb-auth-uri)
        body (json/read-str (:body resp) :key-fn keyword)
        {:keys [user_id app_id]} (:data body)]
    (when (and (= 200 (:status resp))
               (= facebook-user-id user_id)
               (= app-id app_id))
      [:user-social-id facebook-user-id])))

(defn- validate-google
  "Given the request and a map of api keys from authenticating google
  logins, validates the one-time code. Validation returns an
  access_token, which should match the google-access-token provided in
  the request body, and an id_token which can be used to obtain identity
  information about the user. If validation succeeds, returns a vector
  of the form [:user-social-id 'google-id-token'] or nil if validation
  failed."
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
  "A map of privder keys to their respective validation functions."
  {:facebook validate-facebook
   :google validate-google})

(defn- get-provider-validator
  "Given the request and the application auth-config, determines the 3rd
  party auth provider and returns a function that when invoked validates
  the request with the provider and returns a vector of identification
  information needed to create the user."
  [request auth-config]
  (let [login-provider (provider request)
        validator (login-provider provider-validators)
        social-token-map (login-provider auth-config)]
    (fn [] (validator request social-token-map))))

(defn- remove-token-fields
  "Removes specific fields from the request so they are not passed along
  as part of the request after in has been validated."
  [body-params]
  (dissoc body-params :google-code :google-access-token :google-id-token :facebook-app-token :facebook-auth-token :facebook-user-id))

(defn- add-social-data
  "Given the request and the auth-config map, adds the :user-social-id
  key to the body-params of the request and removes any keys that were
  used to validate the request."
  [request auth-config]
  (let [validator (get-provider-validator request auth-config)
        body-params (:body-params request)]
    (when validator
      (remove-token-fields (merge body-params (validator))))))

(defn validate-and-create-user
  "Given a registration request, the auth-config map, and a function
  which takes the request and creates a new user, validates any 3rd
  party access tokens and then invokes the create-user-fn. Returns an
  auth-response whose body is the result of calling the create-user-fn."
  [request auth-config create-user-fn]
  (if (is-social? request)
    (if-let [body-params-with-social (add-social-data request auth-config)]
      (let [create-resp (create-user-fn (assoc request :body-params body-params-with-social))
            user-id (str (get-in create-resp [:body :user-id]))
            api-secret (get-in auth-config [:api :api-secret])]
        (auth-response create-resp api-secret user-id))
      {:status 401})
    (let [create-resp (create-user-fn request)
          user-id (str (get-in create-resp [:body :user-id]))
          api-secret (get-in auth-config [:api :api-secret])]
      (auth-response create-resp api-secret user-id))))

(defn- authenticate-native
  "Given a login request, the auth-config map, and a fn that takes a
  user-id and returns a response map, validates the supplied
  credentials, and, if valid, calls the get-user-fn with the user-id
  from the db. Returns an auth-response."
  [request auth-config get-user-fn]
  (let [{:keys [email password remember]} (:body-params request)
        db-user (first (select users (where {:email email})))
        db-user-id (str (:user_id db-user))
        encrypted-pw (:password db-user)
        pw-salt (:password_salt db-user)
        api-secret (get-in auth-config [:api :api-secret])]
    (when (cr/verify-password password pw-salt encrypted-pw)
      (let [response (get-user-fn db-user-id)]
        (auth-response response api-secret db-user-id :remember? remember)))))

(defn- authenticate-social
  "Given a login request for a user using a 3rd party provider, an
  auth-config map, and a fn that takes the user-id and returns a
  response map, validates the access tokens and, if valid, calls the
  get-user-fn with the user-id from the db. Returns an auth-response."
  [request auth-config get-user-fn]
  (let [{:keys [email]} (:body-params request)
        validator (get-provider-validator request auth-config)]
    (when validator
      (when-let [user-social-id (last (validator))]
        (let [db-user (first (select users (where {:email email})))
              db-user-social-id (:user_social_id db-user)
              db-user-id (str (:user_id db-user))
              api-secret (get-in auth-config [:api :api-secret])]
          (when (= user-social-id db-user-social-id)
            (let [response (get-user-fn db-user-id)]
              (auth-response response api-secret db-user-id))))))))

(defn authenticate
  "Determines if the login request is for native or 3rd party and
  invokes the appropriate function. Calls the get-user-fn if
  authenticated and returns an auth-response or a response with status
  401."
  [{:keys [body-params] :as request} auth-config get-user-fn]
  (if (:password body-params)
    (or (authenticate-native request auth-config get-user-fn) {:status 401})
    (or (authenticate-social request auth-config get-user-fn) {:status 401})))
