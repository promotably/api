(ns api.controllers.users
  (:require [clojure.tools.logging :as log]
            [api.lib.user :refer [salted-pass]]
            [api.models.user :as user]
            [cemerick.friend.credentials :as creds]))

(defn create-new-user!
  [{:keys [params] :as request}]
  (try
    (let [u (user/new-user! params)]
      u)
    (catch clojure.lang.ExceptionInfo ex
      (let [exdata (ex-data ex)]
        (throw (ex-info (.getMessage ex) {:error (:error exdata)
                                          :response {:status 400}}))))))

(defn authenticate-user
  "Authenticates a user"
  [{:keys [username password]}]
  (when-let [u (user/get-auth-record username)]
    (when (creds/bcrypt-verify (salted-pass password) (:crypted_password u))
      (dissoc u :crypted_password))))
