(ns api.controllers.accounts
  (:require [api.lib.coercion-helper :refer [custom-matcher
                                             underscore-to-dash-keys]]
            [api.models.account :as account]
            [api.views.accounts :refer [shape-response-body]]
            [schema.core :as s]
            [schema.coerce :as c]))

(defn- build-response
  [status & {:keys [account cookies session]}]
  (let [response-body (shape-response-body account)]
    (cond-> {:status status
             :body response-body}
            cookies (assoc :cookies cookies)
            session (assoc :session session))))

(defn- parse-and-coerce
    [body request-schema]
    ((c/coercer request-schema
                (c/first-matcher [custom-matcher
                                  c/string-coercion-matcher]))
     (clojure.edn/read-string (slurp body))))

(let [inbound-schema {(s/optional-key :company-name) s/Str
                      (s/optional-key :account-id) s/Uuid
                      (s/required-key :user-id) s/Uuid}]

  (defn get-account
    "Returns an account."
    [{:keys [params]}]
    (if-let [result (account/find-by-account-id (:account-id params))]
      (build-response 200 :account result)
      (build-response 404)))

  (defn create-new-account!
    "Creates a new account in the database."
    [{:keys [params body-params] :as request}]
    (let [coerced-params ((c/coercer
                           inbound-schema
                           (c/first-matcher [custom-matcher
                                             c/string-coercion-matcher]))
                          body-params)
          results (account/new-account! coerced-params)]
      (if results
        (build-response 200 :account (underscore-to-dash-keys results))
        (build-response 409))))

  (defn update-account!
    [{body-params :body-params {:keys [account-id]} :params}]
    (let [coerced-params ((c/coercer
                           inbound-schema
                           (c/first-matcher [custom-matcher
                                             c/string-coercion-matcher]))
                          (merge body-params {:account-id account-id}))
          result (account/update! coerced-params)]
      (if result
        (build-response 200 :account (underscore-to-dash-keys result))
        (build-response 404)))))
