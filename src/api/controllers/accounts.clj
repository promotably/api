(ns api.controllers.accounts
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as s]
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
  [status & {:keys [account error cookies session]}]
  (let [response-body (if account
                        (shape-response-body account)
                        {:error-message (or error "")})]
    (cond-> {:status status
             :headers {"Cache-Control" "max-age=0, no-cache"}
             :body response-body}
            cookies (assoc :cookies cookies)
            session (assoc :session session))))

(defn user-access-to-account?
  [user-id account-id]
  (let [user (user/find-by-user-id user-id)
        user-account-ids (->> user
                              :accounts
                              (map :account-id)
                              set)]
    (contains? user-account-ids account-id)))

(defn get-account
  "Returns an account."
  [{:keys [params user-id] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "accounts-get"}}
        {:keys [account-id user-id]} (shape-to-spec (assoc params :user-id user-id)
                                                    inbound-account-spec)]
    (if (user-access-to-account? user-id account-id)
      (if-let [result (account/find-by-account-id account-id)]
        (merge base-response (build-response 200 :account result))
        (merge base-response (build-response 404 :error "Account does not exist.")))
      (merge base-response (build-response 403 :error "User does not have access to this account.")))))

(defn create-new-account!
  "Creates a new account in the database."
  [{:keys [body-params user-id] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "accounts-create"}}
        account (shape-to-spec (assoc body-params :user-id user-id)
                               inbound-account-spec)
        results (account/new-account! account)]
    (if results
      (merge base-response (build-response 201 :account results))
      (merge base-response (build-response 400 :error "Unable to create account, invalid or missing parameters.")))))

(defn update-account!
  [{:keys [body-params user-id] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "accounts-update"}}
        account (shape-to-spec (assoc body-params :user-id user-id)
                               inbound-account-spec)]
    (if (user-access-to-account? (:user-id account) (:account-id account))
      (let [result (account/update! account)]
        (if result
          (merge base-response (build-response 200 :account result))
          (merge base-response (build-response 400 :error "Unable to update account, invalid or missing parameters.")))))))

(defn create-site-for-account!
  [{:keys [body-params user-id] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "sites-create"}}
        site (shape-to-spec (assoc body-params :user-id user-id)
                            inbound-site-spec)
        id (:id (account/find-by-account-id (:account-id site)))]
    (if (user-access-to-account? (:user-id site) (:account-id site))
      (let [new-site-code (-> (re-find #".*(?://|www\.)(?:www.)?([^\/]+)" (:site-url site))
                              last
                              (s/replace ".com" "")
                              (s/replace "." "-"))]
        (if-not (site/find-by-site-code new-site-code)
          (if-let [result (site/create-site-for-account! id (assoc site :site-code new-site-code))]
            (let [account-with-sites (account/find-by-account-id (:account-id site))]
              (merge base-response (build-response 201 :account account-with-sites)))
            (merge base-response (build-response 400 :error "Unable to create site, invalid or missing parameters.")))
          (merge base-response (build-response 409 :error "Site with this URL already exists."))))
      (merge base-response (build-response 403 :error "User does not have access to this account.")))))

(defn update-site-for-account!
  [{:keys [body-params user-id] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "sites-update"}}
        site (shape-to-spec (assoc body-params :user-id user-id)
                            inbound-site-spec)
        id (:account-id (site/find-by-site-uuid (:site-id site)))
        account-id (:account-id (account/find-by-id id))]
    (if (user-access-to-account? (:user-id site) account-id)
      (if-let [result (site/update-site-for-account! id site)]
        (let [account-with-sites (account/find-by-account-id account-id)]
          (merge base-response (build-response 200 :account account-with-sites)))
        (merge base-response (build-response 400 :error "Unable to update site, invalid or missing parameters.")))
      (merge base-response (build-response 403 :error "User does not have access to this account.")))))
