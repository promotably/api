(ns api.models.email-subscriber
  (:require [api.entities :refer [email-subscribers]]
            [korma.core :refer :all]
            [schema.core :as s]))

(defn create-email-subscriber!
  [{:keys [browser-id :- s/Uuid
           email :- s/Str]}]
  (try (let [es
             (insert email-subscribers
                     (values {:browser_id browser-id
                              :email email}))]
         {:success true :email-subscriber es})
       (catch org.postgresql.util.PSQLException ex
         (when (re-find #"duplicate key value" (.getMessage ex))
           {:success false :error :email-already-exists}))))
