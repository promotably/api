(ns api.controllers.accounts
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [api.lib.coercion-helper :refer [custom-matcher]]
            [api.lib.schema :refer [shape-to-spec
                                    inbound-account-spec
                                    inbound-site-spec]]
            [api.models.user :as user]
            [api.models.account :as account]
            [api.models.site :as site]
            [api.views.accounts :refer [shape-response-body]])
  (:import [java.util UUID]))

(defn- build-response
  [status & {:keys [account cookies session]}]
  (let [response-body (when account
                        (shape-response-body account))]
    (cond-> {:status status
             :body response-body}
            cookies (assoc :cookies cookies)
            session (assoc :session session))))

(defn user-access-to-account?
  [user-id account-id]
  (let [user (user/find-by-user-id user-id)
        user-account-id (or (:account-id-2 user)
                            (:account-id user))]
    (= account-id user-account-id)))

(defn get-account
  "Returns an account."
  [{:keys [params user-id] :as request}]
  (let [{:keys [account-id user-id]} (shape-to-spec (assoc params :user-id user-id)
                                                    inbound-account-spec)]
    (if (user-access-to-account? user-id account-id)
      (if-let [result (account/find-by-account-id account-id)]
        (build-response 200 :account result)
        (build-response 404))
      (build-response 403))))

(defn create-new-account!
  "Creates a new account in the database."
  [{:keys [body-params user-id] :as request}]
  (let [account (shape-to-spec (assoc body-params :user-id user-id)
                               inbound-account-spec)
        results (account/new-account! account)]
    (if results
      (build-response 201 :account results)
      (build-response 400))))

(defn update-account!
  [{:keys [body-params user-id] :as request}]
  (let [account (shape-to-spec (assoc body-params :user-id user-id)
                               inbound-account-spec)]
    (if (user-access-to-account? (:user-id account) (:account-id account))
      (let [result (account/update! account)]
        (if result
          (build-response 200 :account result)
          (build-response 400))))))

(defn create-site-for-account!
  [{:keys [body-params user-id] :as request}]
  (let [site (shape-to-spec (assoc body-params :user-id user-id)
                            inbound-site-spec)
        id (:id (account/find-by-account-id (:account-id site)))]
    (if (user-access-to-account? (:user-id site) (:account-id site))
      (if-let [result (site/create-site-for-account! id site)]
        (let [account-with-sites (account/find-by-account-id (:account-id site))]
          (build-response 201 :account account-with-sites))
        (build-response 400))
      (build-response 403))))

(defn update-site-for-account!
  [{:keys [body-params user-id] :as request}]
  (let [site (shape-to-spec (assoc body-params :user-id user-id)
                            inbound-site-spec)
        id (:id (account/find-by-account-id (:account-id site)))]
    (if (user-access-to-account? (:user-id site) (:account-id site))
      (if-let [result (site/update-site-for-account! id site)]
        (let [account-with-sites (account/find-by-account-id (:account-id site))]
          (build-response 200 :account account-with-sites))
        (build-response 400))
      (build-response 403))))
