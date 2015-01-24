(ns api.controllers.users
  (:require [api.lib.coercion-helper :refer [custom-matcher]]
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
  [user-id]
  (if-let [u (user/find-by-user-id user-id)]
    (let [a (when (and u (:account-id u))
              (account/find-by-id (:account-id u)))
          s (when a (site/find-by-account-id (:account-id u)))
          a-s (assoc a :sites s)
          u-a-s (assoc u :account a-s)]
      (build-response 200 :user u-a-s))
    (build-response 404)))

(defn create-new-user!
  [{:keys [body-params] :as request}]
  (if (or (:password body-params)
          (:user-social-id body-params))
    (let [result (user/new-user! body-params)]
      (build-response 201 :user result))
    (build-response 400)))

(defn update-user!
  [{:keys [body-params] :as request}]
  (let [update-result (user/update-user! body-params)]
    (if update-result
      (get-user (:user-id body-params))
      (build-response 400))))
