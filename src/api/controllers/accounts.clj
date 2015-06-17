(ns api.controllers.accounts
  (:require [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as s]
            [api.controllers.helpers :refer [user-access-to-account?]]
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

(defn- fix-sites-account-ids
  [account]
  (let [account-uuid (:account-id account)
        sites (:sites account)
        fixed-sites (map #(assoc % :account-id account-uuid) sites)]
    (assoc account :sites fixed-sites)))

(defn get-account
  "Returns an account."
  [{:keys [params user-id] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "accounts-get"}}
        {:keys [account-id user-id]} (shape-to-spec (assoc params :user-id user-id)
                                                    inbound-account-spec)]
    (if (user-access-to-account? user-id account-id)
      (if-let [result (account/find-by-account-id account-id)]
        (merge base-response (build-response 200 :account (fix-sites-account-ids result)))
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
      (merge base-response (build-response 201 :account (fix-sites-account-ids results)))
      (merge base-response (build-response 400 :error "Unable to create account, invalid or missing parameters.")))))

(defn update-account!
  [{:keys [body-params user-id] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "accounts-update"}}
        account (shape-to-spec (assoc body-params :user-id user-id)
                               inbound-account-spec)]
    (if (user-access-to-account? (:user-id account) (:account-id account))
      (let [result (account/update! account)]
        (if result
          (merge base-response (build-response 200 :account (fix-sites-account-ids result)))
          (merge base-response (build-response 400 :error "Unable to update account, invalid or missing parameters.")))))))
