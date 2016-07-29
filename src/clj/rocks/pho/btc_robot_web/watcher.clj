(ns rocks.pho.btc-robot-web.watcher
  (:require [mount.core :as mount]
            [clojure.tools.logging :as log]

            [rocks.pho.btc-robot-web.config :refer [env]]
            [rocks.pho.btc-robot-web.utils :as utils]
            [rocks.pho.btc-robot-web.data-analysis :as da]
            [rocks.pho.btc-robot-web.events :as events]))

(mount/defstate history-dir
                :start (:history-dir (:btc-robot env)))

(mount/defstate history-log-file
                :start "")

(mount/defstate kline
                :start (list))

(mount/defstate status
                :start "cny")

(mount/defstate last-check-datetime
                :start "")

(mount/defstate last-kline-log-datetime
                :start "")

(defn find-kline-datetime
  "return -1 : not found
           n : find at index n"
  [a-kline datetime]
  (if (= last-kline-log-datetime "")
    -1
    (loop [index 0]
      (if (>= index (.size a-kline))
        -1
        (let [dt (first (nth a-kline index))]
          (if (= dt datetime)
            index
            (recur (inc index))))))))

(defn kline-watcher
  "kline watcher"
  []
  (try
    (let [fixed-klines (drop-last (utils/get-kline "001"))
          found-index (find-kline-datetime fixed-klines
                                           last-kline-log-datetime)
          new-klines (nthrest fixed-klines (inc found-index))]
      (when-not (empty? new-klines)
        (doseq [kline new-klines]
          (utils/write-a-object kline history-log-file))
        (mount/start-with {#'kline fixed-klines})
        (mount/start-with {#'last-kline-log-datetime (first (last fixed-klines))})))
    (catch Exception e
      (log/error "kline watcher ERROR:" e))))

(defn chance-watcher
  "chance watcher"
  []
  (try
    (let [kline kline
          lastest-datetime (first (last kline))]
      (when (not= lastest-datetime last-check-datetime)
        (case status
          "cny" (when (da/down-up-point? kline 3 -1 1 1) ;; buy point
                  (events/balance-wallet)
                  (when (:success (events/show-hand "buy"))
                    (mount/start-with {#'status "btc"})))
          "btc" (when (da/sell-point? kline 1)  ;; sell point
                  (events/balance-wallet)
                  (when (:success (events/show-hand "sell"))
                    (mount/start-with {#'status "cny"})))
          (throw (Exception. (str "status error: " status))))
        (mount/start-with {#'last-check-datetime lastest-datetime})))
    (catch Exception e
      (log/error "cheance watcher ERROR:" e))))
