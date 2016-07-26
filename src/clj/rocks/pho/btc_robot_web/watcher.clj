(ns rocks.pho.btc-robot-web.watcher
  (:require [mount.core :as mount]
            [clojure.tools.logging :as log]

            [rocks.pho.btc-robot-web.config :refer [env]]
            [rocks.pho.btc-robot-web.utils :as utils]))

(mount/defstate history-dir
                :start (:history-dir (:btc-robot env)))

(defn watch-once
  "watch once"
  []
  (log/info history-dir))
