(ns rocks.pho.btc-robot-web.watcher
  (:require [mount.core :as mount]

            [rocks.pho.btc-robot-web.utils :as utils]))

(mount/defstate history-dir
                :start (:history-dir (:btc-robot env)))

(defn watch-once
  "watch once"
  []
  (log/info history-dir))
