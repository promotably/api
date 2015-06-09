(ns api.controllers.helpers
  (:require [clojure.tools.logging :as log]
            [api.models.user :as user]))

(defn user-access-to-account?
  [user-id account-id]
  (let [user (user/find-by-user-id user-id)
        user-account-ids (->> user
                              :accounts
                              (map :account-id)
                              set)]
    (contains? user-account-ids account-id)))
