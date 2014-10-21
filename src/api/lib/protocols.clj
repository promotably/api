(ns api.lib.protocols)

(defprotocol SessionCache
  "Contract for the implementation of a session cache."
  (init [this]
    "Initialize the cache")
  (shutdown [this]
    "Shutdown the cache"))
