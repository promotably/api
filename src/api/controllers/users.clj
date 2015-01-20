(ns api.controllers.users
  (:require [api.lib.coercion-helper :refer [custom-matcher]]
            [api.models.user :as user]
            [api.models.account :as account]
            [api.models.site :as site]
            [api.views.accounts :refer [shape-get-user shape-create-user shape-update-user]]
            [clojure.tools.logging :as log]
            [schema.core :as s]
            [schema.coerce :as c]))

(let [inbound-schema {(s/required-key :email) s/Str
                      (s/optional-key :password) s/Str
                      (s/optional-key :username) s/Str
                      (s/optional-key :user-social-id) s/Str
                      (s/optional-key :account-id) s/Uuid
                      (s/optional-key :phone) s/Str
                      (s/optional-key :job-title) s/Str
                      (s/optional-key :first-name) s/Str
                      (s/optional-key :last-name) s/Str}]
  (defn create-new-user!
    [{:keys [body-params] :as request}]
    (let [body-params (dissoc body-params :google-code :google-access-token :google-id-token :facebook-auth-token :facebook-app-token :facebook-user-id)
          coerced-params ((c/coercer
                           inbound-schema
                           (c/first-matcher [custom-matcher
                                             c/string-coercion-matcher]))
                          body-params)]
      (if (or (:password coerced-params)
              (:user-social-id coerced-params))
        (let [result (user/new-user! coerced-params)]
          (shape-create-user result))
        (throw (ex-info "User creation requires a password or a social-id"
                        {:reason :api.error/invalid-registration-data}))))))

(let [inbound-schema {(s/required-key :user-id) s/Uuid
                      (s/optional-key :email) s/Str
                      (s/optional-key :username) s/Str
                      (s/optional-key :phone) s/Str
                      (s/optional-key :job-title) s/Str
                      (s/optional-key :first-name) s/Str
                      (s/optional-key :last-name) s/Str
                      (s/optional-key :user-social-id) s/Str
                      (s/optional-key :browser-id) s/Uuid}]
  (defn update-user!
    [{body-params :body-params {:keys [user-id]} :params :as request}]
    (let [coerced-params ((c/coercer
                           inbound-schema
                           (c/first-matcher [custom-matcher
                                             c/string-coercion-matcher]))
                          (assoc body-params :user-id user-id))]
      (shape-update-user (user/update-user! coerced-params)))))

(defn get-user
  [{{:keys [user-id]} :params}]
  (let [u (user/find-by-user-id user-id)]
    (shape-get-user
     {:user u
      :account (when u (account/find-by-id (:account-id u)))})))

(defn lookup-user
  [{{:keys [user-social-id]} :params}]
  (let [u (user/find-by-user-social-id user-social-id)
        a (when u (account/find-by-id (:account-id u)))
        s (when a (first (site/find-by-account-id (:account-id u))))]
    (shape-get-user
     {:user u
      :account a
      :site s})))
