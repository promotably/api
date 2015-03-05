(ns api.models.site
  (:require [clojure.set :refer [rename-keys]]
            [clojure.string :as s]
            [korma.core :refer :all]
            [api.entities :refer :all]
            [api.lib.coercion-helper :refer [underscore-to-dash-keys
                                             dash-to-underscore-keys]])
  (:import [java.util UUID]))

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
  (let [result (first (select sites (where {:site_id site-id})))]
    (underscore-to-dash-keys result)))

(defn find-by-site-id
  [id]
  (underscore-to-dash-keys (first (select sites (where {:id id})))))

(defn get-id-by-site-uuid
  [site-id]
  (:id (first (select sites
                      (fields [:id])
                      (where {:site_id site-id})))))

(defn find-by-name
  [name]
  (underscore-to-dash-keys (first (select sites
                                          (where {:name name})))))

(defn create-site-for-account!
  [account-id site]
  (let [{:keys [name site-url country
                timezone currency language]} site
        new-site (insert sites
                         (values {:account_id account-id
                                  :name name
                                  :site_url site-url
                                  :api_secret (UUID/randomUUID)
                                  :created_at (sqlfn now)
                                  :site_code (-> (re-find #".*(?://|www\.)(?:www.)?([^\/]+)" site-url)
                                                 last
                                                 (s/replace ".com" "")
                                                 (s/replace "." "-"))
                                  :updated_at (sqlfn now)
                                  :country country
                                  :timezone timezone
                                  :currency currency
                                  :language language}))]
    (when new-site
      (underscore-to-dash-keys new-site))))

(let [allowed-keys [:site-code :name :country :timezone
                    :currency :language :site-url]]
  (defn update-site-for-account!
    [account-id site]
    (let [params-for-update (->> (assoc (dash-to-underscore-keys
                                         (select-keys site allowed-keys))
                                   :updated_at (sqlfn now))
                                 (remove (comp nil? second))
                                 (into {}))]
      (update sites
              (set-fields params-for-update)
              (where {:site_id (:site-id site)
                      :account_id account-id})))))
