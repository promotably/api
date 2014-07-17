(ns api.models.email_subscriber
  (:require [api.entities :refer [email-subscribers]]
            [korma.core :refer :all]
            [schema.core :as s]))

(defn create-email-subscriber!
  [{:keys [browser-id :- s/Uuid
           email :- s/Str]}]
  (let [es
        (insert email-subscribers
                (values {:browser_id browser-id
                         :email email}))]
    es))
