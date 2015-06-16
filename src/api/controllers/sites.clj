(ns api.controllers.sites
  (:require [clojure.tools.logging :as log]
            [clojure.string :as s]
            [api.controllers.helpers :refer [user-access-to-account?]]
            [api.lib.schema :refer [shape-to-spec
                                    inbound-site-spec]]
            [api.models.user :as user]
            [api.models.account :as account]
            [api.models.site :as site]
            [api.views.sites :refer [shape-response-body]]))

(defn- build-response
  [status & {:keys [site error cookies session]}]
  (let [response-body (if site
                        (shape-response-body site)
                        {:error-message (or error "")})]
    (cond-> {:status status
             :headers {"Cache-Control" "max-age=0, no-cache"}
             :body response-body}
            cookies (assoc :cookies cookies)
            session (assoc :session session))))

(defn get-site
  [{:keys [params user-id] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "sites-get"}}
        {:keys [user-id site-id account-id]} (shape-to-spec (assoc params :user-id user-id)
                                                            inbound-site-spec)]
    (if-let [site* (site/find-by-site-uuid site-id)]
      (if (user-access-to-account? user-id account-id)
        (merge base-response (build-response 200 :site (merge site* {:account-id account-id})))
        (merge base-response (build-response 403 :error "User does not have access to the account for this site.")))
      (merge base-response (build-response 404 :error "Site with this ID does not exist.")))))

(defn create-site!
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
          (if-let [result (merge (site/create-site-for-account! id (assoc site :site-code new-site-code)) {:account-id (:account-id site)})]
            (merge base-response (build-response 201 :site result))
            (merge base-response (build-response 400 :error "Unable to create site, invalid or missing parameters.")))
          (merge base-response (build-response 409 :error "Site with this URL already exists."))))
      (merge base-response (build-response 403 :error "User does not have access to this account.")))))

(defn update-site!
  [{:keys [body-params user-id] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "sites-update"}}
        site (shape-to-spec (assoc body-params :user-id user-id)
                            inbound-site-spec)
        id (:account-id (site/find-by-site-uuid (:site-id site)))
        account-id (:account-id (account/find-by-id id))]
    (if (user-access-to-account? (:user-id site) account-id)
      (if-let [result (site/update-site-for-account! id site)]
        (merge base-response (build-response 200 :site (merge (site/find-by-site-uuid (:site-id site)) {:account-id account-id})))
        (merge base-response (build-response 400 :error "Unable to update site, invalid or missing parameters.")))
      (merge base-response (build-response 403 :error "User does not have access to this account.")))))
