(ns user
  (:require [mount.core :as mount]
            rocks.pho.btc-robot-web.core))

(defn start []
  (mount/start-without #'rocks.pho.btc-robot-web.core/repl-server))

(defn stop []
  (mount/stop-except #'rocks.pho.btc-robot-web.core/repl-server))

(defn restart []
  (stop)
  (start))


