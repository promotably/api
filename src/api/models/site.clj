(ns api.models.site
  (:require [clojure.set :refer [rename-keys]]
            [korma.core :refer :all]
            [api.entities :refer :all]
            [api.lib.coercion-helper :refer [underscore-to-dash-keys
                                             dash-to-underscore-keys]]))

(defn find-by-account-id
  [account-id]
  (mapv underscore-to-dash-keys
        (select sites
                (where {:account_id account-id}))))

(defn find-by-account-uuid
  [account-uuid]
  (mapv underscore-to-dash-keys
        (select sites
                (join accounts (= :accounts.id :account_id))
                (where {:accounts.account_id account-uuid}))))

(defn find-by-site-uuid
  [site-id]
  (let [result (first (select sites (where {:uuid site-id})))]
    (underscore-to-dash-keys result)))

(defn find-by-site-id
  [id]
  (underscore-to-dash-keys (first (select sites (where {:id id})))))

(defn get-id-by-site-uuid
  [site-id]
  (:id (first (select sites
                      (fields [:id])
                      (where {:uuid site-id})))))

(defn find-by-name
  [name]
  (underscore-to-dash-keys (first (select sites
                                          (where {:name name})))))

(defn create-site-for-account!
  [site]
  (let [{:keys [name site-url api-secret country
                timezone currency language account-id]} site
        new-site (insert sites
                         (values {:account_id account-id
                                  :name name
                                  :site_url site-url
                                  :api_secret api-secret
                                  :country country
                                  :timezone timezone
                                  :currency currency
                                  :language language}))]
    (when new-site
      (underscore-to-dash-keys new-site))))

(let [allowed-keys [:site-code :name :country :timezone
                    :currency :language :site-url]]
  (defn update-site-for-account!
    [site]
    (let [params-for-update (dash-to-underscore-keys
                             (select-keys site allowed-keys))]
      (update sites
              (set-fields params-for-update)
              (where {:uuid (:site-id site)
                      :account_id (:account-id site)})))))
