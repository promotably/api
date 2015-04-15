(ns api.controllers.email-subscribers
  (:require [api.models.email-subscriber :as es]
            [api.views.email-subscribers :refer [render-create]]))

(defn create-email-subscriber!
  [{:keys [params] :as request}]
  (merge {:context {:cloudwatch-endpoint "email-subscribers-create"}}
         (render-create
          (es/create-email-subscriber! (:email params)))))
