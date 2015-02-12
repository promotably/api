(ns api.integration.metrics
  (:require
    [api.fixtures.event-data :as fix]
    [clj-http.client :as client]
    [midje.sweet :refer :all]
    [api.integration.helper :refer :all]
    [api.system :refer [current-system] :as system]
    [api.core :as core]))

(defn request-metrics [path site-id]
  (client/get (str "http://localhost:3000/api/v1/sites/" site-id path)
              {:body nil
               :headers {"Cookie" (build-auth-cookie-string)}
               :content-type :json
               :accept :json
               :throw-exceptions false}))

(against-background [(before :contents
                             (do (when (nil? system/current-system)
                                   (core/go {:port 3000 :repl-port 55555}))
                                 (migrate-down)
                                 (migrate-up)
                                 (load-fixture-set fix/fixture-set)))
                     (after :contents
                            (comment migrate-down))]

  (def site (api.models.site/find-by-name "site-1"))
  (def site-id (:site-id site))

  (fact-group :integration
              (fact "Can route to controller.api.metrics.get-revenue"
                    (let [r (request-metrics "/metrics/revenue" site-id)]
                      (:body r) => "get-revenue"
                      (:status r) => 200))

              (fact "Can route to controller.api.metrics.get-lift"
                    (let [r (request-metrics "/metrics/lift" site-id)]
                      (:body r) => "get-lift"
                      (:status r) => 200))

              (fact "Can route to controller.api.metrics.get-promos"
                    (let [r (request-metrics "/metrics/promos" site-id)]
                      (:body r) => "get-promos"
                      (:status r) => 200))

              (fact "Can route to controller.api.metrics.get-rco"
                    (let [r (request-metrics "/metrics/rco" site-id)]
                      (:body r) => "get-rco"
                      (:status r) => 200))))

