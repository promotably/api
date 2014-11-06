(ns api.models.email-subscriber
  (:require [api.entities :refer [email-subscribers]]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [korma.core :refer :all]
            [schema.core :as s]))

(let [mailchimp-api-key "b85b815650c8168387c3aa416d730250-us9"
      mailchimp-api-endpoint "https://us9.api.mailchimp.com/2.0/lists/subscribe.json"
      mailchimp-list-id "47cb1c484c"]
  (defn- post-to-mailchimp!
    [email]
    (try
      (let [resp (client/post mailchimp-api-endpoint
                              {:body (json/write-str {:apikey mailchimp-api-key
                                                      :id mailchimp-list-id
                                                      :email {:email email}})})]
        (log/info "Posted email subscriber to mailchimp")
        (when-not (= (:status resp) 200)
          (log/errorf "Non-200 response while posting email subscriber to mailchimp %d" 200)))
      (catch java.lang.Throwable t
        (log/error t "Error while posting email subscriber to mailchimp")))))

(defn create-email-subscriber!
  [email]
  (future (post-to-mailchimp! email))
  (try
    (let [res (insert email-subscribers
                      (values {:email email
                               :created_at (sqlfn now)}))]
      (if (seq res)
        {:success true}
        {:success false}))
    (catch Throwable t
      (log/error t "Error while saving email subscriber to database")
      {:success false})))

