(ns api.controllers.users
  (:require [clojure.tools.logging :as log]
            [api.models.user :as user]
            [api.models.account :as account]
            [api.views.accounts :refer [shape-get-user]]))

(defn create-new-user!
  [{:keys [params] :as request}]
  (try
    (let [u (user/new-user! params)]
      u)
    (catch clojure.lang.ExceptionInfo ex
      (let [exdata (ex-data ex)]
        (throw (ex-info (.getMessage ex) {:error (:error exdata)
                                          :response {:status 400}}))))))

(defn get-user
  [{:keys [params] :as request}]
  (let [u (user/find-by-user-social-id (:user-social-id params))]
    (shape-get-user
     {:user u
      :account (when u (account/find-by-id (:account-id u)))})))
