(ns api.controllers.email-subscribers
  (:require [api.models.email-subscriber :as es]
            [api.views.email-subscribers :refer [render-create]]))

(defn create-email-subscriber!
  [{:keys [params] :as request}]
  (render-create
   (es/create-email-subscriber!
    {:browser-id (java.util.UUID/fromString (:browser-id params))
     :email (:email params)})))
