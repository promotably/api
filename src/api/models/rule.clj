(ns api.models.rule
  (:require [clojure.tools.logging :as log]
            [api.entities :refer [time-frame-rules time-frames]]
            [schema.core :as s]
            [schema.macros :as sm]))

(def RuleTypeEnum (s/enum :time-frame :limit))

(def BaseRule {(s/required-key :type) RuleTypeEnum
               (s/required-key :promo-id) s/Int})

(def TimeFrame {(s/required-key :start) org.joda.time.DateTime
                (s/required-key :end) org.joda.time.DateTime})

(def InboundTimeFrameRule (merge BaseRule
                                 {(s/required-key :time-frames) [TimeFrame]}))

(defmulti *new-rule! :type)

(defmethod *new-rule! :time-frame
  [{:keys [promo-id time-frames] :as params}]
  (log/debugf "Creating new time-frame rule for promo %d" promo-id))

(sm/defn ^:always-validate new-rule!
  "Creates a new rule in the database"
  [params :- (s/either BaseRule InboundTimeFrameRule)]
  (*new-rule! params))
