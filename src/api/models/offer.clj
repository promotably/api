(ns api.models.offer
  (:require [clojure.tools.logging :as log]
            [clj-time.core :refer [before? after? now]]
            [clj-time.coerce :refer [from-sql-date]]
            [clojure.set :refer [rename-keys intersection]]
            [api.db :refer :all]
            [api.entities :refer :all]
            [api.lib.coercion-helper :refer [custom-matcher underscore-to-dash-keys]]
            [api.lib.schema :refer :all]
            [api.models.helper :refer :all]
            [api.models.offer-condition :as c]
            [api.models.linked-products :as lp]
            [api.models.redemption :as rd]
            [api.models.site :as site]
            [api.models.promo :as promo]
            [api.util :refer [hyphenify-key]]
            [clojure.core.cache :as cache]
            [korma.core :refer :all]
            [korma.db :refer [transaction] :as kdb]
            [schema.core :as s]
            [schema.macros :as sm]
            [schema.coerce :as sc]))

(def OffersCache (atom (cache/ttl-cache-factory {} :ttl 300000))) ;; TTL 5 minutes

(defn db-to-offer
  "Convert a database result to a offer that obeys the OfferSchema"
  [r]
  (let [matcher (sc/first-matcher [custom-matcher sc/string-coercion-matcher])
        coercer (sc/coercer OutboundOffer matcher)
        hyphenified-params (underscore-to-dash-keys r)
        cleaned (apply dissoc
                       hyphenified-params
                       (for [[k v] hyphenified-params :when (nil? v)] k))
        promo (first (promo/find-by-id (:promo-id cleaned)))
        renamed (-> cleaned
                    (assoc :presentation
                      {:page (keyword (:presentation-page cleaned))
                       :display-text (:presentation-display-text cleaned)
                       :type (keyword (:presentation-type cleaned))})
                    (assoc :reward {:type (if (:dynamic cleaned)
                                            :dynamic-promo
                                            :promo)
                                    :promo-id (:uuid promo)})
                    ((fn [o] (if (= (-> o :reward :type) :dynamic-promo)
                               (assoc-in o [:reward :expiry-in-minutes]
                                         (:expiry-in-minutes o))
                               o)))
                    (dissoc :promo-id :dynamic :expiry-in-minutes)
                    (dissoc :presentation-type
                            :presentation-page
                            :presentation-display-text))
        conditions (map
                    (comp unwrap-jdbc
                          c/db-to-condition)
                    (:offer-conditions renamed))
        done (-> renamed
                 (dissoc :offer-conditions)
                 (assoc :conditions conditions))]
    (coercer done)))

(defn lookup-by
  [fields]
  {:pre [(map? fields)]}
  (select offers (where fields)))

(defn exists?
  [site-id code]
  (not (empty? (lookup-by {:site_id site-id :code code}))))

(defn by-offer-uuid
  [site-id offer-id]
  (seq (lookup-by {:site_id site-id :uuid offer-id})))

(defn by-site-uuid-and-offer-uuid
  [site-uuid offer-uuid]
  (seq (select offers
               (join sites (= :sites.id :site_id))
               (where {:sites.uuid site-uuid
                       :uuid offer-uuid}))))

(sm/defn new-offer!
  "Creates a new offer in the database"
  [{:keys [site-id code name display-text
           presentation
           reward
           conditions
           offer-id] :as params}]
  (let [{p-type :type p-display-text :display-text p-page :page} presentation
        {:keys [promo-id expiry-in-minutes type]} reward
        p (first (promo/find-by-site-and-uuid site-id promo-id))]
    (cond

     (exists? site-id code)
     {:success false
      :error :already-exists
      :message (format "A offer with code %s already exists" code)}

     (not p)
     {:success false
      :error :invalid-promo
      :message (format "Promo %s does not exist" promo-id)}

     :else
     (transaction
      (let [new-values {;; TODO: should probably have this flag.
                        ;; :active true
                        :site_id site-id
                        :code code
                        :name name
                        :display_text display-text
                        :promo_id (:id p)
                        :dynamic (if (= :dynamic-promo type)
                                   true
                                   false)
                        :expiry_in_minutes expiry-in-minutes
                        :presentation_type (clojure.core/name p-type)
                        :presentation_page (clojure.core/name p-page)
                        :presentation_display_text p-display-text
                        :created_at (sqlfn now)
                        :updated_at (sqlfn now)
                        :uuid (java.util.UUID/randomUUID)}
            result (insert offers (values new-values))
            the-offer (db-to-offer result)]
        (when (seq conditions)
          (c/create-conditions! (map #(-> %
                                          (assoc :uuid (java.util.UUID/randomUUID))
                                          (assoc :offer-id (:id the-offer)))
                                     conditions)))
        {:success true})))))

(defn update-offer!
  "Updates a offer in the database"
  [offer-uuid
   {:keys [code name display-text
           presentation
           reward
           conditions]
    :as params}]
  (let [site-id (:site-id params)
        {p-type :type p-display-text :display-text p-page :page} presentation
        {:keys [promo-id expiry-in-minutes]} reward
        offer-uuid (java.util.UUID/fromString offer-uuid)
        offer (first (by-offer-uuid site-id offer-uuid))
        promo (first (promo/find-by-site-and-uuid site-id promo-id))
        id (:id offer)]
    (cond

     (not offer)
     {:success false
      :error :not-found
      :message (format "Offer id %s does not exist." offer-uuid)}

     (not promo)
     {:success false
      :error :not-found
      :message (format "Promo id %s does not exist." promo-id)}

     :else
     (let [new-values {;; TODO: should probably have this flag.
                       ;; :active true
                       :site_id site-id
                       :code code
                       :name name
                       :display_text display-text
                       :promo_id (:id promo)
                       :expiry_in_minutes expiry-in-minutes
                       :presentation_type (clojure.core/name p-type)
                       :presentation_page (clojure.core/name p-page)
                       :presentation_display_text p-display-text
                       :updated_at (sqlfn now)}
           result (update offers
                          (set-fields new-values)
                          (where {:id id}))]
       (c/update-conditions! id
                             (map #(-> %
                                       (assoc :uuid (java.util.UUID/randomUUID))
                                       (assoc :offer-id id))
                                  conditions))
       {:success true}))))

(sm/defn find-by-site-uuid
  "Finds all offers for a given site id. Returns a collection (empty
  array if no results found)"
  [site-uuid :- s/Uuid]
  (map db-to-offer
       (select offers
               (with offer-conditions)
               (join sites (= :sites.id :site_id))
               (where {:sites.uuid site-uuid}))))

(sm/defn find-by-uuid
  "Finds a offer by uuid."
  [offer-uuid :- s/Uuid]
  (let [results (select offers
                        (with offer-conditions)
                        (where {:offers.uuid offer-uuid}))]
    (first (map db-to-offer results))))

(sm/defn delete-by-uuid
  "Deletes a offer by uuid."
  [offer-uuid :- s/Uuid]
  (prn "delete" offer-uuid)
  (let [found (find-by-uuid offer-uuid)]
    (transaction
     (c/delete-conditions! (:id found))
     (delete offers (where {:offers.uuid offer-uuid})))))

(sm/defn ^:always-validate find-by-site-uuid-and-code
  "Finds a offer with the given site id and code combination.
  Returns nil if no results found"
  [site-uuid :- s/Uuid
   offer-code :- s/Str]
  (let [row (first (select offers
                           (with offer-conditions)
                           (join sites (= :sites.id :site_id))
                           (where {:sites.uuid site-uuid
                                   :offers.code (clojure.string/upper-case
                                                 offer-code)})))]
    (if row (db-to-offer row))))

(defn get-offers-for-site
  ;; Returns cached offers for a site
  [site-id]
  (if (cache/has? @OffersCache site-id)
    (swap! OffersCache #(cache/hit % site-id))
    (swap! OffersCache #(cache/miss % site-id (find-by-site-uuid site-id))))
  (cache/lookup @OffersCache site-id))

(defn valid?
  [context {:keys [conditions] :as offer}]
  (let [validated-conditions (map #(c/validate context %) conditions)]
    (if (seq validated-conditions)
      (every? true? validated-conditions)
      false)))
