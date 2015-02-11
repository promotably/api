(ns api.controllers.offers
  (:require [api.lib.coercion-helper :refer [custom-matcher]]
            [api.lib.schema :refer :all]
            [api.models.offer :as offer]
            [api.models.site :as site]
            [api.views.offers :refer [shape-offer
                                      shape-lookup
                                      shape-new-offer
                                      shape-rcos]]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.data.json :refer [read-str write-str]]
            [clojure.tools.logging :as log]
            [slingshot.slingshot :refer [throw+ try+]]
            [schema.coerce :as c]
            [schema.core :as s]))

(def query-schema {:site-id s/Uuid
                   (s/optional-key :promotably-auth) s/Str
                   :offer-code s/Str})

(defn lookup-offers
  [{:keys [params] :as request}]
  (let [{:keys [site-id] :as coerced-params}
        ((c/coercer OfferLookup
                    (c/first-matcher [custom-matcher
                                      c/string-coercion-matcher]))
         params)
        found (offer/find-by-site-uuid (:site-id coerced-params))]
    (when (= schema.utils.ErrorContainer (type coerced-params))
      (throw+ {:type :argument-error :body-params params :error coerced-params}))
    (shape-lookup found)))

(defn show-offer
  [{:keys [params body] :as request}]
  (let [offer-uuid (java.util.UUID/fromString (:offer-id params))
        site-uuid (java.util.UUID/fromString (:site-id params))
        offers (offer/by-site-uuid-and-offer-uuid site-uuid offer-uuid)]
    (shape-offer (offer/db-to-offer (first offers)))))

;; TODO: enforce ownership
(defn delete-offer!
  [{:keys [params body] :as request}]
  (let [{:keys [offer-id]} params
        offer-uuid (java.util.UUID/fromString offer-id)
        found (offer/find-by-uuid offer-uuid)]
    (if found
      (do
        (offer/delete-by-uuid offer-uuid)
        {:status 200})
      {:status 404})))

(defn create-new-offer!
  [{:keys [params body-params] :as request}]
  (let [site-uuid (java.util.UUID/fromString (:site-id body-params))
        ;; TODO: Handle the site not being found
        id (site/get-id-by-site-uuid site-uuid)
        ;; required since JSON input results in strings and we need keywords
        conditions (map #(assoc %1 :type (keyword (:type %1))) (:conditions body-params))
        body-params (-> body-params
                        (update-in [:reward :type] #(keyword %1))
                        (update-in [:presentation :type] #(keyword %1))
                        (assoc :conditions conditions))
        coerced-params ((c/coercer NewOffer
                                   (c/first-matcher [custom-matcher
                                                     c/string-coercion-matcher]))
                        body-params)]
    (when (= schema.utils.ErrorContainer (type coerced-params))
      (throw+ {:type :argument-error :body-params params :error coerced-params}))
    (let [result (offer/new-offer! (assoc coerced-params :site-id id))]
      (shape-new-offer result))))

(defn update-offer!
  [{:keys [params body-params] :as request}]
  (let [{:keys [offer-id]} params
        site-uuid (java.util.UUID/fromString (:site-id body-params))
        ;; required since JSON input results in strings and we need keywords
        conditions (map #(assoc %1 :type (keyword (:type %1))) (:conditions body-params))
        body-params (-> body-params
                        (update-in [:reward :type] #(keyword %1))
                        (update-in [:presentation :type] #(keyword %1))
                        (assoc :conditions conditions))
        coerced-params ((c/coercer NewOffer
                                   (c/first-matcher [custom-matcher
                                                     c/string-coercion-matcher]))
                        (dissoc body-params :offer-id))
        ;; TODO: Handle the site not being found
        id (site/get-id-by-site-uuid site-uuid)]
    (when (= schema.utils.ErrorContainer (type coerced-params))
      (throw+ {:type :argument-error :body-params params :error coerced-params}))
    (shape-new-offer
     (offer/update-offer! offer-id (assoc coerced-params :site-id id)))))

(defn query-offer
  [{:keys [params] :as request}]
  (let [{:keys [site-id offer-code] :as coerced-params}
        ((c/coercer query-schema
                    (c/first-matcher [custom-matcher
                                      c/string-coercion-matcher]))
         params)
        the-offer (offer/find-by-site-uuid-and-code site-id
                                                    offer-code)]
    (if-not the-offer
      {:status 404 :body "Can't find that offer"}
      (do
        {:body (shape-offer the-offer)}))))

(defn uuid-from-request-or-new
  [key params request]
  (java.util.UUID/fromString (or (get params key) (get request key))))

(defn get-available-offers
  [{:keys [params session] :as request}]
  (let [site-id (uuid-from-request-or-new :site-id params request)
        shopper-id (uuid-from-request-or-new :shopper-id params request)
        site-shopper-id (uuid-from-request-or-new :site-shopper-id params request)
        valid-offers (filter #(offer/valid? {:shopper-id shopper-id
                                             :site-id site-id
                                             :session session
                                             :offer %
                                             :site-shopper-id site-shopper-id} %)
                             (offer/get-offers-for-site site-id))
        the-offer (cond-> []
                          (seq valid-offers) (conj (rand-nth valid-offers)))]
    ;; 'The Offer' is a collection for now until we change the
    ;; response format in the view.
    (shape-rcos session the-offer)))
