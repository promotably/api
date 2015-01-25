(ns api.controllers.users
  (:require [api.controllers.helper :refer [shape-inbound
                                            inbound-user-spec]]
            [api.models.user :as user]
            [api.models.account :as account]
            [api.models.site :as site]
            [api.views.users :refer [shape-response-body]]
            [clojure.tools.logging :as log]))

(defn- build-response
  [status & {:keys [user cookies session]}]
  (let [response-body (shape-response-body user)]
    (cond-> {:status status
             :body response-body}
            cookies (assoc :cookies cookies)
            session (assoc :session session))))

(defn get-user
  [str-user-id]
  (let [{:keys [user-id]} (shape-inbound {:user-id str-user-id} inbound-user-spec)]
    (if-let [u (user/find-by-user-id user-id)]
      (let [a (when (and u (:account-id u))
                (account/find-by-id (:account-id u)))
            s (when a (site/find-by-account-id (:account-id u)))
            a-s (assoc a :sites s)
            u-a-s (assoc u :account a-s)]
        (build-response 200 :user u-a-s))
      (build-response 404))))

(defn create-new-user!
  [{:keys [body-params] :as request}]
  (let [user (shape-inbound body-params inbound-user-spec)]
    (if (or (:password user)
            (:user-social-id user))
      (let [result (user/new-user! user)]
        (build-response 201 :user result))
      (build-response 400))))

(defn update-user!
  [{:keys [body-params] :as request}]
  (let [user (shape-inbound body-params inbound-user-spec)
        update-result (user/update-user! user)]
    (if update-result
      (get-user (str (:user-id user)))
      (build-response 400))))
