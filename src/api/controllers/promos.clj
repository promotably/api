(ns api.controllers.promos
  (:require [api.lib.schema :refer :all]
            [api.models.promo :as promo]
            [api.models.site :as site]
            [api.lib.coercion-helper :refer [transform-map
                                             remove-nils
                                             remove-nested-nils
                                             make-trans
                                             custom-matcher
                                             underscore-to-dash-keys]]
            [api.models.site :as site]
            [api.models.event :as event]
            [api.views.promos :refer [shape-promo
                                      shape-lookup
                                      shape-new-promo
                                      shape-update-promo
                                      shape-validate
                                      shape-calculate]]
            [api.models.offer :as offer :refer [fallback-to-exploding]]
            [api.lib.auth :refer [parse-auth-string auth-valid? transform-auth]]
            [clojure.data.json :refer [read-str write-str]]
            [clojure.tools.logging :as log]
            [schema.coerce :as c]
            [schema.core :as s]))

(def query-schema {:site-id s/Uuid
                   (s/optional-key :promotably-auth) s/Str
                   (s/optional-key :shopper-id) s/Str
                   (s/optional-key :site-shopper-id) s/Uuid
                   (s/optional-key :site-session-id) s/Uuid
                   (s/optional-key :request-format-version) s/Str
                   :code s/Str})

(defn lookup-promos
  [{:keys [params] :as request}]
  (let [{:keys [site-id] :as coerced-params}
        ((c/coercer PromoLookup
                    (c/first-matcher [custom-matcher
                                      c/string-coercion-matcher]))
         params)
        found-site (site/find-by-site-uuid site-id)
        r (if found-site
            {:results (promo/find-by-site-uuid site-id)}
            {:error :site-not-found})]
    (shape-lookup r)))

;; TODO: enforce ownership
(defn delete-promo!
  [{:keys [params body] :as request}]
  (let [{:keys [promo-id]} params
        promo-uuid (java.util.UUID/fromString promo-id)
        found (promo/find-raw :uuid promo-uuid)]
    (if found
      (do
        (promo/delete-by-uuid (:site-id found) promo-uuid)
        {:status 200})
      {:status 404})))

(defn create-new-promo!
  [{:keys [params body-params] :as request}]
  (let [coerced-params ((c/coercer NewPromo
                                   (c/first-matcher [custom-matcher
                                                     c/string-coercion-matcher]))
                        body-params)
        p (condp = (class coerced-params)
            schema.utils.ErrorContainer coerced-params
            (promo/new-promo! (assoc coerced-params
                                :site-id (site/get-id-by-site-uuid
                                          (:site-id coerced-params)))))]
    (shape-new-promo (:site-id coerced-params) p)))

(defn update-promo!
  [{:keys [params body-params] :as request}]
  (let [{:keys [promo-id]} params
        coerced-params ((c/coercer NewPromo
                                   (c/first-matcher [custom-matcher
                                                     c/string-coercion-matcher]))
                        (dissoc body-params :promo-id))
        p (condp = (class coerced-params)
            schema.utils.ErrorContainer coerced-params
            (promo/update-promo! promo-id (assoc coerced-params
                                            :site-id (site/get-id-by-site-uuid
                                                      (:site-id coerced-params)))))]
    (shape-update-promo (:site-id coerced-params) p)))

(defn show-promo
  [{:keys [params body] :as request}]
  (let [site-uuid (java.util.UUID/fromString (:site-id params))
        site (site/find-by-site-uuid site-uuid)
        promo-uuid (java.util.UUID/fromString (:promo-id params))
        promo (promo/find-by-site-and-uuid (:id site) promo-uuid)]
    (shape-promo {:promo promo})))

;; TODO: Check auth
(defn query-promo
  [{:keys [params cloudwatch-recorder] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "promos-query"}}
        matcher (c/first-matcher [custom-matcher c/string-coercion-matcher])
        coercer (c/coercer query-schema matcher)
        {:keys [site-id code] :as coerced-params} (coercer params)]
    (cond
     (or (nil? code) (= "" code))
     (do
       (cloudwatch-recorder "promo-query-error" 1 :Count)
       (cloudwatch-recorder "promo-query-error" 1 :Count
                            :dimensions {:site-id (str site-id)})
       (log/logf :error "Nil or empty promo code. Params: %s" params)
       (merge base-response {:status 400
                             :session (:session request)}))
     :else
     (let [code (clojure.string/upper-case code)
           the-promo (promo/find-by-site-uuid-and-code site-id code)
           [offer-id offer-promo] (fallback-to-exploding cloudwatch-recorder
                                                         site-id code)]
       (shape-promo {:promo (or the-promo offer-promo)})))))

(def coerce-site-id
  (make-trans #{:site-id}
              #(vector :site (if (string? %2)
                               (-> %2 java.util.UUID/fromString site/find-by-site-uuid)
                               (site/find-by-site-uuid %2)))))

(defn prep-incoming
  [params]
  (let [dbg (partial prn "---")]
    (-> params
        underscore-to-dash-keys
        remove-nils
        remove-nested-nils
        coerce-site-id
        ;; (doto dbg)
        transform-auth)))

(defn validate-promo
  [{:keys [code params body-params headers cloudwatch-recorder] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "promos-validate"}}
        matcher (c/first-matcher [custom-matcher c/string-coercion-matcher])
        coercer (c/coercer PromoValidationRequest matcher)
        coerced-params (cond-> body-params
                               (-> body-params :code nil?) (assoc :code (:code params))
                               true (assoc :promotably-auth
                                      (or (:promotably-auth params)
                                          (get headers "promotably-auth")))
                               true prep-incoming
                               true coercer)]

    (if (= (class coerced-params) schema.utils.ErrorContainer)
      (do
        (cloudwatch-recorder "promo-validate-schema-error" 1 :Count)
        (merge base-response {:status 400 :body (write-str (:error coerced-params))}))
      (do
        (let [site-id (-> coerced-params :site :site-id)
              the-site (site/find-by-site-uuid site-id)
              code (-> coerced-params :code clojure.string/upper-case)
              found-promo (if (and the-site code)
                            (promo/find-by-site-uuid-and-code site-id code))
              [offer-id offer-promo] (if (and the-site code)
                                       (fallback-to-exploding cloudwatch-recorder
                                                              site-id
                                                              code))
              the-promo (or found-promo offer-promo)]

          ;; For debugging
          ;; (clojure.pprint/pprint the-promo)
          ;; (clojure.pprint/pprint coerced-params)

          (cond
            (not the-promo)
            (do
              (cloudwatch-recorder "promo-validate-not-found" 1 :Count)
              (cloudwatch-recorder "promo-validate-not-found" 1 :Count
                                   :dimensions {:site-id (str site-id)})
              (merge base-response {:status 404 :body "Can't find that promo"
                                    :session (:session request)}))

            (not (auth-valid? site-id
                              (-> coerced-params :site :api-secret)
                              (:auth coerced-params)
                              (assoc request :body body-params)))
            (do
              (cloudwatch-recorder "promo-validate-auth-error" 1 :Count)
              (cloudwatch-recorder "promo-validate-auth-error" 1 :Count
                                   :dimensions {:site-id (str site-id)})
              (merge base-response {:status 403
                                    :session (:session request)}))
            :else
            (let [[v errors] (promo/valid? the-promo (assoc coerced-params
                                                       :site the-site))
                  resp (merge {:uuid (:uuid the-promo) :code code}
                              (if errors
                                {:valid false :messages errors}
                                {:valid true :messages []}))]
              (cloudwatch-recorder "promo-validate-success" 1 :Count)
              (cloudwatch-recorder "promo-validate-success" 1 :Count
                                   :dimensions {:site-id (str site-id)
                                                :valid (if errors "0" "1")})
              (merge base-response {:status 201
                                    :session (:session request)
                                    :body (shape-validate resp)}))))))))

(defn calculate-promo
  [{:keys [params body-params cloudwatch-recorder] :as request}]
  (let [base-response {:context {:cloudwatch-endpoint "promos-calculate"}}
        matcher (c/first-matcher [custom-matcher c/string-coercion-matcher])
        coercer (c/coercer PromoValidationRequest matcher)
        coerced-params (-> body-params
                           (assoc :promotably-auth (:promotably-auth params))
                           prep-incoming
                           coercer)
        site-id (-> coerced-params :site :site-id)
        code (-> coerced-params :code clojure.string/upper-case)
        [offer-id offer-promo] (fallback-to-exploding cloudwatch-recorder
                                                      site-id
                                                      code)
        the-promo (or (promo/find-by-site-uuid-and-code site-id code)
                      offer-promo)
        [context errors] (promo/valid? the-promo coerced-params)]
    (cond
     (not the-promo)
     (do
       (cloudwatch-recorder "promo-calculate-not-found" 1 :Count)
       (cloudwatch-recorder "promo-calculate-not-found" 1 :Count
                            :dimensions {:site-id (str site-id)})
       (merge base-response {:status 404
                             :body "Can't find that promo"
                             :session (:session request)}))

     errors
     (do
       (cloudwatch-recorder "promo-calculate-errors" 1 :Count)
       (cloudwatch-recorder "promo-calculate-errors" 1 :Count
                            :dimensions {:site-id (str site-id)})
       (merge base-response {:status 400
                             :session (:session request)}))

     (not (auth-valid? site-id
                       (-> coerced-params :site :api-secret)
                       (:auth coerced-params)
                       (assoc request :body body-params)))
     (do
       (cloudwatch-recorder "promo-calculate-auth-error" 1 :Count)
       (cloudwatch-recorder "promo-calculate-auth-error" 1 :Count
                            :dimensions {:site-id (str site-id)})
       (merge base-response {:status 403
                             :session (:session request)}))

     :else
      (let [[amt context errors] (promo/discount-amount the-promo
                                                        context
                                                        errors)]
        (cloudwatch-recorder "promo-calculate-success" 1 :Count)
        (cloudwatch-recorder "promo-calculate-success" 1 :Count
                             :dimensions {:site-id (str site-id)})
        (merge base-response {:status 201
                              :session (:session request)
                              :body (shape-calculate {:valid true :discount amt})})))))
