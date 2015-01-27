(ns api.models.account
  (:require [korma.core :refer :all]
            [api.lib.coercion-helper :refer [underscore-to-dash-keys]]
            [api.entities :refer [accounts users sites]]
            [api.models.site :as site]
            [api.models.user :as user]))

(defn find-by-id
  [id]
  (underscore-to-dash-keys (first
                            (select accounts
                                    (with sites)
                                    (where {:id id})))))

(defn find-by-account-id
  [account-id]
  (underscore-to-dash-keys (first
                            (select accounts
                                    (with sites)
                                    (where {:account_id account-id})))))

(defn new-account!
  "Creates a new account in the database."
  [user-id {:keys [company-name] :as account}]
  (let [a (insert accounts
                  (values {:company_name company-name
                           :created_at (sqlfn now)
                           :updated_at (sqlfn now)}))
        user (update users
                     (set-fields {:account_id (:id a)})
                     (where {:user_id user-id}))]
    (when (and a user)
      (find-by-id (:id a)))))

(defn update!
  [{:keys [account-id company-name] :as account}]
  (when-let [db-account (find-by-account-id account-id)]
    (when-let [a (update accounts
                         (set-fields {:company_name company-name})
                         (where {:account_id account-id}))]
      (find-by-account-id account-id))))
