(ns api.controllers.offers
  (:require [api.config :as config]
            [api.kinesis :as kinesis]
            [api.lib.coercion-helper :refer [custom-matcher]]
            [api.lib.schema :refer :all]
            [api.models.offer :as offer]
            [api.models.site :as site]
            [api.models.promo :as promo]
            [api.system :refer [current-system]]
            [api.views.offers :refer [shape-offer
                                      shape-lookup
                                      shape-new-offer]]
            [api.cloudwatch :as cw]
            [clj-time.core :as t]
            [clj-time.coerce :as t-coerce]
            [clj-time.format :as tf]
            [api.views.helper :refer [view-value-helper]]
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

(defn select-offer
  [session offers]
  (let [test-bucket (:test-bucket session)
        control? (= :control test-bucket)]
    (if (and (not control?) (seq offers))
      (rand-nth offers))))

(defn create-response
  [promo offer is-dynamic? exploding-code expiry]
  (cond-> {:promo {:conditions (:conditions promo)}
           :offer-id (:uuid offer)
           :presentation (:presentation offer)
           :is-limited-time is-dynamic?
           :code (if is-dynamic? exploding-code (:code promo))
           :active true}
          is-dynamic? (assoc :expires expiry)))

(defn find-valid-offers
  [shopper-id site-shopper-id site-id session]
  (filter #(let [context {:shopper-id shopper-id
                          :site-id site-id
                          :session session
                          :offer %
                          :site-shopper-id site-shopper-id}]
             (offer/valid? context %))
          (offer/find-by-site-uuid site-id)))

(defn get-available-offers
  [kinesis-comp {:keys [params session cookies] :as request}]
  (if-let [active (:active-offer session)]
    {:body nil}
    (let [site-id (uuid-from-request-or-new :site-id params request)
          shopper-id (uuid-from-request-or-new :shopper-id params request)
          site-shopper-id (uuid-from-request-or-new :site-shopper-id params request)
          valid-offers (find-valid-offers shopper-id
                                          site-shopper-id
                                          site-id
                                          session)
          the-offer (select-offer session valid-offers)
          {:keys [promo-id expiry-in-minutes]} (:reward the-offer)
          promo (if the-offer (promo/find-by-uuid promo-id))
          is-dynamic? (= :dynamic-promo (-> the-offer :reward :type))
          [exploding-code expiry] (if is-dynamic?
                                    (offer/generate-exploding-code the-offer))
          qualified-event {:event-name :shopper-qualified-offers
                           :site-id site-id
                           :shopper-id shopper-id
                           :site-shopper-id site-shopper-id}
          response-data (create-response promo
                                         the-offer
                                         is-dynamic?
                                         exploding-code
                                         expiry)]
      (if (empty? valid-offers)
        {:body nil :offer-qualification-event qualified-event}
        (let [qualified-event (assoc qualified-event :offer-ids (mapv :uuid valid-offers))
              assignment-event (-> qualified-event
                                   (assoc :event-name :offer-made)
                                   (dissoc :offer-ids)
                                   (assoc :promo-id (:uuid promo))
                                   (assoc :offer-id (:uuid the-offer)))
              now (t-coerce/to-string (t/now))]
          (cond-> {:offer-qualification-event qualified-event
                   :body (write-str response-data
                                    :value-fn (fn [k v] (view-value-helper v)))}
                  the-offer (assoc-in [:session :active-offer] the-offer)
                  is-dynamic? (assoc-in [:session :active-offer :code] exploding-code)
                  is-dynamic? (assoc-in [:session :active-offer :expires] expiry)
                  the-offer (assoc-in [:session :last-offer-at] now)
                  the-offer (assoc :offer-assignment-event assignment-event)
                  is-dynamic? (assoc-in [:offer-assignment-event :code] exploding-code)
                  is-dynamic? (assoc-in [:offer-assignment-event :expiry] expiry)))))))

(defn wrap-record-rco-events
  "Record offer events."
  [handler]
  (fn [{:keys [session] :as request}]
    (let [k (:kinesis current-system)
          response (handler request)
          assignment-data (:new-bucket-assignment response)
          sid (:session/key response)
          qualified-event (:offer-qualification-event response)
          assignment-event (:offer-assignment-event response)
          ev-base {:session-id sid :control-group (= (:bucket assignment-data) :control)}]
      (if sid
        (do
          (when qualified-event
            (cw/put-metric "rco-qualification")
            (kinesis/record-event! k :shopper-qualified-offers (merge qualified-event ev-base)))
          (when assignment-event
            (cw/put-metric "rco-assignment")
            (kinesis/record-event! k :offer-made (merge assignment-event ev-base))))
        (do
          (log/logf :error "Error recording rco: missing session id.")
          (cw/put-metric "rco-session-id-error")))
      response)))

