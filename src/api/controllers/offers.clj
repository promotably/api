(ns api.controllers.offers
  (:require [api.lib.coercion-helper :refer [custom-matcher]]
            [api.lib.schema :refer :all]
            [api.models.offer :as offer]
            [api.models.site :as site]
            [api.views.offers :refer [shape-offer
                                      shape-lookup
                                      shape-new-offer]]
            [clojure.data.json :refer [read-str]]
            [clojure.tools.logging :as log]
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
        found (offer/find-by-site-uuid site-id)]
    (shape-lookup (map offer/db-to-offer found))))

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
  [{:keys [params body] :as request}]
  (let [input-edn (clojure.edn/read-string (slurp body))
        site-id (:site-id input-edn)
        ;; TODO: Handle the site not being found
        id (site/get-id-by-site-uuid site-id)
        coerced-params ((c/coercer NewOffer
                                   (c/first-matcher [custom-matcher
                                                     c/string-coercion-matcher]))
                        input-edn)]
    (shape-new-offer
     (offer/new-offer! (assoc coerced-params :site-id id)))))

(defn update-offer!
  [{:keys [params body] :as request}]
  (let [{:keys [offer-id]} params
        input-edn (clojure.edn/read-string (slurp body))
        site-uuid (:site-id input-edn)
        coerced-params ((c/coercer NewOffer
                                   (c/first-matcher [custom-matcher
                                                     c/string-coercion-matcher]))
                        (dissoc input-edn :offer-id))
        ;; TODO: Handle the site not being found
        id (site/get-id-by-site-uuid site-uuid)]
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

