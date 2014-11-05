(ns api.models.email-subscriber
  (:require [api.entities :refer [email-subscribers]]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [korma.core :refer :all]
            [schema.core :as s]))

(def mailchimp-api-key "b85b815650c8168387c3aa416d730250-us9")
(def mailchimp-api-endpoint "https://us9.api.mailchimp.com/2.0/lists/subscribe.json")
(def mailchimp-list-id "47cb1c484c")

(defn create-email-subscriber!
  [email]
  (try
    (client/post mailchimp-api-endpoint
                 {:body (json/write-str {:apikey mailchimp-api-key
                                         :id mailchimp-list-id
                                         :email {:email email}})})))
