(ns api.controllers.users
  (:require [api.lib.schema :refer [shape-to-spec
                                    inbound-user-spec]]
            [api.models.user :as user]
            [api.models.account :as account]
            [api.models.site :as site]
            [api.views.users :refer [shape-response-body]]
            [clojure.tools.logging :as log]))

(defn- build-response
  [status & {:keys [user error cookies session]}]
  (let [response-body (if user
                        (shape-response-body user)
                        {:error-message (or error "")})]
    (cond-> {:status status
             :body response-body}
            cookies (assoc :cookies cookies)
            session (assoc :session session))))

(defn get-user
  [str-user-id]
  (let [{:keys [user-id]} (shape-to-spec {:user-id str-user-id} inbound-user-spec)]
    (if-let [u (user/find-by-user-id user-id)]
      (build-response 200 :user u)
      (build-response 404 :error "User does not exist."))))

(defn create-new-user!
  [{:keys [body-params] :as request}]
  (let [user (shape-to-spec body-params inbound-user-spec)]
    (if (or (:password user)
            (:user-social-id user))
      (let [result (user/new-user! user)]
        (build-response 201 :user result))
      (build-response 400 :error "New user must provide a password or use a social provider."))))

(defn update-user!
  [{:keys [body-params user-id] :as request}]
  (let [user (shape-to-spec (assoc body-params :user-id user-id)
                            inbound-user-spec)
        update-result (user/update-user! user)]
    (if update-result
      (get-user (str (:user-id user)))
      (build-response 400 :error "Unable to update user, invalid or missing parameters."))))
