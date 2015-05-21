(ns api.integration.vbucket
  (:require
   [api.fixtures.exploding :as fix]
   [api.integration.helper :refer :all]
   [api.system :as system]
   [api.core :as core]
   [api.models.site]
   [api.models.event :as event]
   [api.lib.seal :refer [hmac-sha1 url-encode]]
   [clj-http.client :as client]
   [cheshire.core :refer :all]
   [korma.core :refer :all]
   [midje.sweet :refer :all]))

(against-background [(before :contents
                             (do (init!)
                                 (load-fixture-set fix/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

  (def test-uuid #uuid "9aca0ad1-94d4-4edf-bbbe-827c3208ba63")
  (def control-uuid #uuid "4969f7bf-ea6f-4c84-9fee-212eb4453997")

  (defn- get-rcos
    [site-id site-shopper-id & {:keys [cookies] :as opts}]
    (client/get (str (test-target-url) "/api/v1/rco")
                (merge {:throw-exceptions false
                        :query-params {"site-id" (str site-id)
                                       "site-shopper-id" (str site-shopper-id)}}
                       opts)))

  (fact-group :integration

              (facts "Check that ID is bucketed as control"
                (get-rcos fix/dynamic-site-id-2 control-uuid)
                (Thread/sleep 12000)
                (let [e1 (event/last-event fix/dynamic-site-id-2 control-uuid "bucket-assigned")
                      e2 (event/last-event fix/dynamic-site-id-2 control-uuid "shopper-qualified-offers")]
                  (:control_group e1) => true
                  (:control_group e2) => true))

              (facts "Check that ID is bucketed as test"
                (get-rcos fix/dynamic-site-id-2 test-uuid)
                (Thread/sleep 12000)
                (let [e1 (event/last-event fix/dynamic-site-id-2 test-uuid "bucket-assigned")
                      e2 (event/last-event fix/dynamic-site-id-2 test-uuid "offer-made")]
                  (:control_group e1) => false
                  (:control_group e2) => false))))



