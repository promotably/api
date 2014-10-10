(ns api.db
  (:require [api.system :refer [system]]
            [korma.core :refer :all]
            [korma.db :as kdb]
            [joda-time :refer [date-time to-millis-from-epoch]]
            [clojure.java.jdbc :as jdbc]))

(defn db-version
  "Gets the current version of the database"
  []
  (let [db-config (get-in system [:app :database])
        _ (println db-config)
        v (:version
           (first
            (jdbc/query
             db-config
             ["SELECT version FROM migrations ORDER BY version DESC LIMIT 1"])))]
    (Long/parseLong (or v "0"))))

(defn update-db-version
  "Updates the current version of the database"
  [version]
  (let [db-config (get-in system [:app :database])]
    (jdbc/insert! db-config :migrations {:version version})))

(comment
  (use 'korma.core)
  (use 'api.entities)
  (let [site-uuid (java.util.UUID/fromString "26b28c70-2144-4427-aee3-b51031b08426")]
    (delete sites (where {:sites.uuid site-uuid}))
    (insert sites
            (values {:uuid site-uuid
                     :account_id 1
                     :name "test"
                     :created_at (sqlfn now)
                     :updated_at (sqlfn now)}))
    (let [result (select sites (where {:sites.uuid site-uuid}))]
      (delete promos (where {:promos.site_id (-> result first :id)}))
      (insert promos
              (values {:uuid (java.util.UUID/randomUUID)
                       :site_id (-> result first :id)
                       :name "test"
                       :code "TWENTYOFF"
                       :incept_date (java.sql.Date. (to-millis-from-epoch
                                                     (date-time "2014-01-01")))
                       :expiry_date (java.sql.Date. (to-millis-from-epoch
                                                     (date-time "2015-01-01")))
                       :created_at (sqlfn now)
                       :updated_at (sqlfn now)
                       :individual_use false
                       :exclude_sale_items true
                       :max_usage_count 1000
                       :current_usage_count 0
                       :type "percent"
                       :amount 20.0
                       :active true
                       :apply_before_tax true
                       :free_shipping false
                       :minimum_cart_amount 10.00
                       :minimum_product_amount 0
                       :usage_limit_per_user -1}))
      (api.models.promo/find-by-site-uuid-and-code
       site-uuid
       "TWENTYOFF")))
)
