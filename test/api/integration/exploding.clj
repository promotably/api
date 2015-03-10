(ns api.integration.exploding
  (:require
   [api.fixtures.exploding :as fix]
   [api.integration.helper :refer :all]
   [api.route :as route]
   [api.system :as system]
   [api.core :as core]
   [api.models.site]
   [api.models.event :as event]
   [api.models.promo :as promo]
   [api.models.offer :as offer]
   [api.lib.seal :refer [hmac-sha1 url-encode]]
   [clj-http.client :as client]
   [cheshire.core :refer :all]
   [korma.core :refer :all]
   [clojure.data.json :as json]
   [midje.sweet :refer :all]))

(against-background [(before :contents
                             (do (when (nil? system/current-system)
                                   (core/go {:port 3000 :repl-port 55555}))
                                 (migrate-or-truncate)
                                 (load-fixture-set fix/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

  (def max-retries 8)
  (def site (api.models.site/find-by-name "site-dynamic-1"))
  (def site-id (:site-id site))
  (def api-secret (:api-secret site))
  (def promos (api.models.promo/find-by-site-uuid site-id false))

  (defn- basic-request-data
    [sid code]
    {:site-id (str sid)
     :code code
     :shopper-id (str (java.util.UUID/randomUUID)),
     :site-shopper-id (str (java.util.UUID/randomUUID))})

  (defn- get-rcos
    [site-id site-shopper-id & {:keys [cookies] :as opts}]
    (client/get "http://localhost:3000/api/v1/rco"
                (assoc-in (merge {:throw-exceptions false
                                  :query-params {"site-id" (str site-id)
                                                 "site-shopper-id" (str site-shopper-id)}}
                                 opts)
                          [:query-params "xyzzy"]
                          1)))

  (fact-group :integration

              (facts "Expired offer check..."
                (let [r1 (get-rcos fix/dynamic-site-id-2
                                   fix/dynamic-site-shopper-id)
                      pr1 (json/read-str (:body r1) :key-fn keyword)
                      vid (get-in r1 [:cookies "promotably" :value])
                      sid (get-in r1 [:cookies "promotably-session" :value])
                      code (:code pr1)]
                  ;; let scribe catch up
                  (loop [tries 1
                         e (event/find-offer fix/dynamic-site-id-2 code)]
                    (if (and (not e) (< tries max-retries))
                      (do
                        (Thread/sleep 3000)
                        (recur (+ 1 tries)
                               (event/find-offer fix/dynamic-site-id-2 code)))
                      (-> e :data :code) => code))
                  (let [rq-body (json/write-str (basic-request-data fix/dynamic-site-id-2
                                                                    code))
                        path (url-encode (str "/api/v1/promos/query/" code))
                        sig-hash (compute-sig-hash "localhost"
                                                   "GET"
                                                   path
                                                   rq-body
                                                   (str fix/dynamic-site-id-2)
                                                   (str api-secret))
                        r (query-promo code (str fix/dynamic-site-id-2) rq-body sig-hash)
                        response-body (json/read-str (:body r) :key-fn keyword)]
                    (:status r) => 404)))

              (facts "Get one and only one dynamic offer"
                (let [r1 (get-rcos site-id
                                   fix/dynamic-site-shopper-id)
                      pr1 (json/read-str (:body r1) :key-fn keyword)
                      vid (get-in r1 [:cookies "promotably" :value])
                      sid (get-in r1 [:cookies "promotably-session" :value])
                      r2 (get-rcos site-id
                                   fix/dynamic-site-shopper-id
                                   :cookies {"promotably" {:value vid}
                                             "promotably-session" {:value sid}})
                      pr2 (json/read-str (:body r2) :key-fn keyword)
                      code (:code pr1)]
                  (:status r1) => 200
                  (:status r2) => 200
                  pr1 => (contains {:expires string?
                                    :promo {:conditions []},
                                    :presentation {:display-text nil,
                                                   :page "product-detail",
                                                   :html nil
                                                   :css nil
                                                   :theme nil
                                                   :type "lightbox"},
                                    :is-limited-time true,
                                    :code string?,
                                    :active true})
                  (get-in r2 [:cookies "promotably-session" :value]) => nil
                  pr2 => nil
                  ;; let scribe catch up
                  (loop [tries 1]
                    (Thread/sleep 3000)
                    (let [e (event/find-outstanding-offer site-id code)]
                      (if e
                        (-> e :data :code) => code
                        (if (< tries max-retries)
                          (recur (+ 1 tries))
                          (-> e :data :code) => code))))
                  (let [rq-body (json/write-str (basic-request-data site-id code))
                        path (url-encode (str "/api/v1/promos/query/" code))
                        sig-hash (compute-sig-hash "localhost"
                                                   "GET"
                                                   path
                                                   rq-body
                                                   (str site-id)
                                                   (str api-secret))
                        r (query-promo code (str site-id) rq-body sig-hash)
                        response-body (json/read-str (:body r) :key-fn keyword)]
                    (:code response-body) => code
                    (:status r) => 200)))))

