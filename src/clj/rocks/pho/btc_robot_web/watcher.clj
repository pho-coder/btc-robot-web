(ns rocks.pho.btc-robot-web.watcher
  (:require [mount.core :as mount]
            [clojure.tools.logging :as log]

            [rocks.pho.btc-robot-web.config :refer [env]]
            [rocks.pho.btc-robot-web.utils :as utils]))

(mount/defstate history-dir
                :start (:history-dir (:btc-robot env)))

(mount/defstate kline
                :start (list))

(defn kline-watcher
  "kline watcher"
  []
  (try
    (mount/start-with {#'kline })
    (catch Exception e
      (log/error "kline watcher ERROR:" e))))

(defn chance-watcher
  "chance watcher"
  []
  (try
    (catch Exception e
      (log/error "cheance watcher ERROR:" e))))
