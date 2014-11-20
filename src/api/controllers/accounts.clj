(ns api.controllers.accounts
  (:require [api.lib.coercion-helper :refer [custom-matcher
                                             underscore-to-dash-keys]]
            [api.models.account :as account]
            [api.models.site :as site]
            [api.models.user :as user]
            [api.views.accounts :refer [shape-create shape-update
                                        shape-lookup-account]]
            [schema.core :as s]
            [schema.coerce :as c]))

(defn- parse-and-coerce
    [body request-schema]
    ((c/coercer request-schema
                (c/first-matcher [custom-matcher
                                  c/string-coercion-matcher]))
     (clojure.edn/read-string (slurp body))))

(let [inbound-schema {(s/required-key :email) s/Str
                      (s/optional-key :browser-id) s/Uuid
                      (s/required-key :first-name) s/Str
                      (s/required-key :last-name) s/Str
                      (s/required-key :user-social-id) s/Str
                      (s/optional-key :site-code) s/Str
                      (s/optional-key :api-secret) s/Uuid
                      (s/optional-key :site-url) s/Str
                      (s/optional-key :company-name) s/Str
                      (s/optional-key :account-id) s/Uuid}]

  (defn lookup-account
    [{{:keys [user-social-id]} :params}]
    (let [u (user/find-by-user-social-id user-social-id)
          a (when u (account/find-by-id (:account-id u)))
          s (when a (first (site/find-by-account-id (:account-id u))))]
      (shape-lookup-account {:user u :account a :site s})))

  (defn create-new-account!
    "Creates a new account in the database. Also creates a user and a site"
    [{:keys [params body-params] :as request}]
    (let [coerced-params ((c/coercer
                           inbound-schema
                           (c/first-matcher [custom-matcher
                                             c/string-coercion-matcher]))
                          body-params)
          results (account/new-account! coerced-params)]
      (shape-create (underscore-to-dash-keys results))))

  (defn update-account!
    [{body-params :body-params {:keys [account-id]} :params}]
    (let [coerced-params ((c/coercer
                           inbound-schema
                           (c/first-matcher [custom-matcher
                                             c/string-coercion-matcher]))
                          (merge body-params {:account-id account-id}))
          result (account/update! coerced-params)]
      (shape-update (underscore-to-dash-keys result)))))
