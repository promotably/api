(ns api.env)

(defonce env (atom nil))

(defn init!
  "Initialize env subsystem."
  [& [the-env]]
  (reset! env (or the-env
                  (System/getenv "ENV")
                  (System/getProperty "ENV")
                  "dev")))

(defmacro when-env [some-env & body]
  `(when (= ~some-env @env)
     ~@body))

