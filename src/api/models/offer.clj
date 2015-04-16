(ns api.models.offer
  (:require [clojure.tools.logging :as log]
            [clj-time.coerce :refer [from-sql-date] :as t-coerce]
            [clj-time.core :refer [before? after? now time-zone-for-id to-time-zone
                                   from-time-zone date-time year month day] :as t]
            [clojure.set :refer [rename-keys intersection]]
            [api.entities :refer :all]
            [api.lib.coercion-helper :refer [custom-matcher underscore-to-dash-keys]]
            [api.lib.schema :refer :all]
            [api.models.helper :refer :all]
            [api.models.offer-condition :as c]
            [api.models.linked-products :as lp]
            [api.models.redemption :as rd]
            [api.models.site :as site]
            [api.models.promo :as promo]
            [api.models.event :as event]
            [api.util :refer [hyphenify-key]]
            [api.db]
            [clojure.core.cache :as cache]
            [korma.core :refer :all]
            [korma.db :refer [transaction] :as kdb]
            [crypto.random :as random]
            [schema.core :as s]
            [schema.macros :as sm]
            [schema.coerce :as sc]))

;; TODO: remove this
(def OffersCache (atom (cache/ttl-cache-factory {} :ttl 300000))) ;; TTL 5 minutes

(defn fallback-to-exploding
  [cloudwatch-recorder site-id code]
  (let [{:keys [offer-id] :as offer-event} (event/find-outstanding-offer cloudwatch-recorder site-id code)]
    (when offer-event
      (let [offer-promo (promo/find-by-uuid (-> offer-event
                                                :data
                                                :promo-id
                                                java.util.UUID/fromString))]
        (if offer-promo [(-> offer-event :data :offer-id)
                         (assoc offer-promo :code code)])))))

(defn lookup-exploding
  [site-id code]
  (let [{:keys [offer-id] :as offer-event} (event/find-offer site-id code)]
    (when offer-event
      (let [offer-promo (promo/find-by-uuid (-> offer-event
                                                :data
                                                :promo-id
                                                java.util.UUID/fromString))]
        (if offer-promo [(-> offer-event :data :offer-id)
                         (assoc offer-promo :code code)])))))

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
                       :type (keyword (:presentation-type cleaned))
                       :html (:html cleaned)
                       :css (:css cleaned)
                       :theme (:theme cleaned)})
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
                            :display-text
                            :html
                            :css
                            :theme
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
  [{:keys [conditions withs joins]}]
  {:pre [(map? conditions)]}
  (-> (cond-> (select* offers)
              conditions (where conditions)
              withs (with withs))
      (select)))

(sm/defn find-by-site-uuid
  "Finds all offers for a given site id. Returns a collection (empty
  array if no results found)"
  [site-uuid :- s/Uuid]
  (let [results (select offers
                        (with offer-conditions)
                        (join sites (= :sites.id :site_id))
                        (where {:sites.site_id site-uuid}))]
    (map db-to-offer results)))

(sm/defn find-by-uuid
  "Finds an offer by uuid."
  [offer-uuid :- s/Uuid]
  (let [results (lookup-by {:conditions {:offers.uuid offer-uuid}
                            :withs offer-conditions})]
    (db-to-offer (first results))))

(defn exists?
  [site-id code]
  (not (empty? (lookup-by {:conditions {:site_id site-id :code code}}))))

(defn by-offer-uuid
  [site-id offer-id]
  (seq (lookup-by {:conditions {:site_id site-id :uuid offer-id}})))

(defn by-site-uuid-and-offer-uuid
  [site-uuid offer-uuid]
  (seq (select offers
               (with offer-conditions)
               (join sites (= :sites.id :site_id))
               (where {:sites.site_id site-uuid
                       :uuid offer-uuid}))))

(sm/defn new-offer!
  "Creates a new offer in the database"
  [{:keys [site-id code name display-text
           presentation
           reward
           conditions
           offer-id
           html
           css
           theme
           active] :as params}]
  (let [{p-type :type p-display-text :display-text p-page :page} presentation
        {:keys [promo-id expiry-in-minutes type]} reward
        p (promo/find-by-site-and-uuid site-id promo-id true)]

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
      (let [new-values (cond-> {:site_id site-id
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
                                :uuid (java.util.UUID/randomUUID)
                                :html html
                                :css css
                                :theme theme}
                         (not (nil? active)) (assoc :active active))
            result (insert offers (values new-values))
            the-offer (db-to-offer result)]
        (when (seq conditions)
          (c/create-conditions! (map #(-> %
                                          (assoc :uuid (java.util.UUID/randomUUID))
                                          (assoc :offer-id (:id the-offer)))
                                     conditions)))
        {:success true :offer (find-by-uuid (:uuid the-offer))})))))

(defn update-offer!
  "Updates a offer in the database"
  [offer-uuid
   {:keys [code name display-text
           presentation
           reward
           conditions
           html
           css
           theme
           active]
    :as params}]
  (let [site-id (:site-id params)
        {p-type :type p-display-text :display-text p-page :page} presentation
        {:keys [promo-id expiry-in-minutes]} reward
        offer-uuid (java.util.UUID/fromString offer-uuid)
        offer (first (by-offer-uuid site-id offer-uuid))
        promo (promo/find-by-site-and-uuid site-id promo-id true)
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
     (let [new-values {
                       :active active
                       :site_id site-id
                       :code code
                       :name name
                       :dynamic (= :dynamic-promo (keyword (:type reward)))
                       :display_text display-text
                       :promo_id (:id promo)
                       :expiry_in_minutes expiry-in-minutes
                       :presentation_type (clojure.core/name p-type)
                       :presentation_page (clojure.core/name p-page)
                       :presentation_display_text p-display-text
                       :updated_at (sqlfn now)
                       :html html
                       :css css
                       :theme theme}
           result (update offers
                          (set-fields new-values)
                          (where {:id id}))]
       (c/update-conditions! id
                             (map #(-> %
                                       (assoc :uuid (java.util.UUID/randomUUID))
                                       (assoc :offer-id id))
                                  conditions))
       {:success true :offer (find-by-uuid offer-uuid)}))))

(sm/defn delete-by-uuid
  "Deletes a offer by uuid."
  [offer-uuid :- s/Uuid]
  (let [found (find-by-uuid offer-uuid)]
    (transaction
     (delete offers (where {:offers.uuid offer-uuid})))))

(sm/defn ^:always-validate find-by-site-uuid-and-code
  "Finds a offer with the given site id and code combination.
  Returns nil if no results found"
  [site-uuid :- s/Uuid
   offer-code :- s/Str]
  (let [row (first (select offers
                           (with offer-conditions)
                           (join sites (= :sites.id :site_id))
                           (where {:sites.site_id site-uuid
                                   :offers.code (clojure.string/upper-case
                                                 offer-code)})))]
    (if row (db-to-offer row))))

(defn valid?*
  [context {:keys [reward conditions active] :as offer}]
  (let [{:keys [promo-id expiry-in-minutes]} reward
        promo (promo/find-by-uuid promo-id)
        validated-conditions (map #(c/valid? context %) conditions)]
    (cond
      (= active false)
      false
      (not promo)
      (do
        (log/logf :error "Can't find promo for offer.  Offer uuid %s, promo uuid %s"
                  (:uuid offer)
                  promo-id)
        false)
      (not (promo/valid-for-offer? promo))
      false
      (not (seq conditions))
      true
      (every? true? validated-conditions)
      true
      :else
      false)))

(defn valid?
  [context offer]
  (try
    (valid?* context offer)
    (catch Throwable t
      (log/error t "Exception while attempting to validate offer")
      (log/errorf "Offer: %s" offer)
      (log/errorf "Context: %s" context)
      (throw t))))

(defn generate-exploding-code
  [{:keys [reward] :as offer}]
  (let [{:keys [promo-id expiry-in-minutes]} reward
        promo (promo/find-by-uuid promo-id)
        code (-> (random/url-part 4)
                 clojure.string/upper-case
                 (clojure.string/replace #"[O0]" "9"))
        expiry (t-coerce/to-string (t/plus (t/now) (t/minutes expiry-in-minutes)))]
    [code expiry]))
