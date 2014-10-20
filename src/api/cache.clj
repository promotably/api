(ns api.cache
  (:require [joda-time :as jt]
            [clojure.core.reducers :as r]
            [clojure.tools.logging :as log]
            [api.lib.protocols :refer (SessionCache)]
            [ring.middleware.session.store :as ss])
  (:import (java.util.concurrent Executors TimeUnit
                                 ScheduledExecutorService)
           [java.util UUID]))

(defn- clean-cache [cache]
  (swap! cache (fn [c]
                 (remove (fn [[session-key data]]
                           (jt/after? (:expires data) (jt/date-time))) c))))

(defn- init-scheduler [cache ^ScheduledExecutorService scheduler]
  (.scheduleWithFixedDelay
   scheduler
   #(clean-cache cache)
   5 5 TimeUnit/MINUTES))

(defrecord ApiSessionCache [cache scheduler]
  SessionCache
  (init [this]
    (log/info :INITIALIZING "Cache is starting...")
    (init-scheduler cache scheduler)
    this)
  (shutdown [this]
    (log/info :SHUTTINGDOWN "Cache is shutting down...")
    (.shutdownNow ^ScheduledExecutorService scheduler)
    (reset! cache nil)
    this)
  ss/SessionStore
  (read-session [this session-id]
    (when session-id
      (session-id @cache)))
  (write-session [this session-id data]
    (let [session-id* (or session-id
                          (UUID/randomUUID))
          expires (jt/plus (jt/date-time) (jt/hours 2))
          data* (assoc (merge (session-id* @cache) data)
                  :expires expires)]
      (swap! cache (fn [c]
                     (assoc c session-id* data*)))
      session-id*))
  (delete-session [this session-id]
    (swap! cache (fn [c]
                   (dissoc c [session-id])))
    nil))

(defn api-session-cache []
  (let [session-cache (atom {})
        scheduler (Executors/newSingleThreadScheduledExecutor)]
    (->ApiSessionCache session-cache scheduler)))
