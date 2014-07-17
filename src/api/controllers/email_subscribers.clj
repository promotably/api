(ns api.controllers.email_subscribers
  (:require [api.models.email_subscriber :as es]))

(defn create-email-subscriber!
  [{:keys [params] :as request}]
  (println params)
  (es/create-email-subscriber! {:browser-id (java.util.UUID/fromString (:browser-id params))
                                :email (:email params)}))
