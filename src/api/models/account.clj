(ns api.models.account
  (:require [korma.core :refer :all]
            [api.lib.coercion-helper :refer [underscore-to-dash-keys]]
            [api.entities :refer [accounts users sites users-accounts]]
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
  [{:keys [company-name user-id] :as account}]
  (let [a (insert accounts
                  (values {:company_name company-name
                           :created_at (sqlfn now)
                           :updated_at (sqlfn now)}))
        u-to-a (insert users-accounts
                       (values {:users_id (:id (user/find-by-user-id user-id))
                                :accounts_id (:id a)}))]
    (when (and a u-to-a)
      (find-by-id (:id a)))))

(defn update!
  [{:keys [account-id company-name] :as account}]
  (when-let [db-account (find-by-account-id account-id)]
    (when-let [a (update accounts
                         (set-fields {:company_name company-name})
                         (where {:account_id account-id}))]
      (find-by-account-id account-id))))
