(ns api.controllers.offers
  (:require [api.lib.coercion-helper :refer [custom-matcher]]
            [api.lib.schema :refer :all]
            [api.models.offer :as offer]
            [api.models.site :as site]
            [api.views.offers :refer [shape-offer
                                      shape-lookup
                                      shape-new-offer]]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.data.json :refer [read-str write-str]]
            [clojure.tools.logging :as log]
            [schema.coerce :as c]
            [schema.core :as s]))

(def query-schema {:site-id s/Uuid
                   (s/optional-key :promotably-auth) s/Str
                   :offer-code s/Str})

(def mock-offer {:offers [{:coupon {:code "TWENTYOFF"
                                    :description "20% off all items"
                                    :reward-amount 20
                                    :reward-type :percent
                                    :reward-applied-to :cart
                                    :reward-tax :after-tax
                                    :conditions [{:type :total-discounts
                                                  :total-discounts 500.00}
                                                 {:type :item-count
                                                  :item-count 3}
                                                 {:type :min-order-value
                                                  :amount 50.00}
                                                 {:type :individual-use}]}
                           :rco {:display-text "Thank you for shopping with us. We'd like to offer you a one-time only 20% discount on your order. But hurry this offer expires soon!"
                                 :presentation-type :fly-in
                                 :presentation-page :any
                                 :dynamic-coupon-code "ASDSDF"}}]})

(defn lookup-offers
  [{:keys [params] :as request}]
  (let [{:keys [site-id] :as coerced-params}
        ((c/coercer OfferLookup
                    (c/first-matcher [custom-matcher
                                      c/string-coercion-matcher]))
         params)
        found (offer/find-by-site-uuid site-id)]
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

(def the-chars "ABCDEFGHIJKLMNOPQRSTUVWXYZ")

(defn- mock-offers-response
  []
  (-> mock-offer
      (assoc-in [:offers 0 :rco :expires]
                (tf/unparse (tf/formatters :date-time-no-ms)
                            (t/plus (t/now) (t/minutes 15))))
      (assoc-in [:offers 0 :rco :presentation-type] (let [r (rand-int 100)] (if (< 50 r) :fly-in
                                                                                :lightbox)))
      (assoc-in [:offers 0 :rco :dynamic-coupon-code] (reduce
                                                       #(let [c (str (nth the-chars (rand (count the-chars))))
                                                              _ %2]
                                                          (str %1 c))
                                                       ""
                                                       (range 6)))))

(defn- real-life-offers-response
  [site-id visitor-id]
  (let [available-offers (offer/get-offers-for-site site-id)
        ;;valid-offers (shape-lookup (filter offer/valid? available-offers))
        ]
    {:offers []}))

(defn get-available-offers
  [{:keys [params session] :as request}]
  (let [site-id (java.util.UUID/fromString (or (:site-id params) (:site_id params)))
        visitor-id (java.util.UUID/fromString (or (:visitor-id params)
                                                  (:visitor_id params)
                                                  (:visitor-id request)))
        mock? (if-not (nil? (:mock params))
                (Boolean/parseBoolean (:mock params))
                false)
        product-view-count (get-in session [:product-view-count] 0)
        resp (if (or mock? (>= product-view-count 3))
               (mock-offers-response)
               (real-life-offers-response site-id visitor-id))]
    (if (>= product-view-count 3)
      {:body (write-str resp)
       :headers {"Content-Type" "application/json"}
       :session {:product-view-count 0}}
      {:body (write-str resp)
       :headers {"Content-Type" "application/json"}})))
