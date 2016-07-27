(ns rocks.pho.btc-robot-web.watcher
  (:require [mount.core :as mount]
            [clojure.tools.logging :as log]

            [rocks.pho.btc-robot-web.config :refer [env]]
            [rocks.pho.btc-robot-web.utils :as utils]
            [rocks.pho.btc-robot-web.data-analysis :as da]
            [rocks.pho.btc-robot-web.events :as events]))

(mount/defstate history-dir
                :start (:history-dir (:btc-robot env)))

(mount/defstate kline
                :start (list))

(mount/defstate status
                :start "")

(mount/defstate last-check-datetime
                :start "")

(defn kline-watcher
  "kline watcher"
  []
  (try
    (mount/start-with {#'kline (utils/get-kline "001")})
    (catch Exception e
      (log/error "kline watcher ERROR:" e))))

(defn chance-watcher
  "chance watcher"
  []
  (try
    (let [kline kline
          lastest-datetime (first (last kline))
          history-kline (drop-last kline)]
      (when (not= lastest-datetime last-check-datetime)
        (case status
          "cny" (when (da/down-up-point? history-kline 3 1 1 1) ;; buy point
                  (log/info (da/recently-continued-times history-kline))
                  (events/balance-wallet)
                  (when (:success (events/show-hand "buy"))
                    (mount/start-with {#'status "btc"})))
          "btc" (when (da/sell-point? history-kline 1)  ;; sell point
                  (log/info (da/recently-continued-times history-kline))
                  (events/balance-wallet)
                  (when (:success (events/show-hand "sell"))
                    (mount/start-with {#'status "cny"})))
          (throw (Exception. (str "status error: " status))))
        (mount/start-with {#'last-check-datetime lastest-datetime})))
    (catch Exception e
      (log/error "cheance watcher ERROR:" e))))
