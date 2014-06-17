(ns api.controllers.accounts
  (:require [api.models.account :as account]
            [cemerick.friend :as friend]))

(defn create-new-account!
  [{:keys [params] :as request}]
  (let [user (friend/current-authentication)]
    (account/new-account! (:id user) params)))
