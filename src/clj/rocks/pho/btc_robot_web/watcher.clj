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

(mount/defstate buy-point
                :start {:down-times-least 3M
                        :down-price-least -1M
                        :up-times-least 1M
                        :up-price-least 1M})

(mount/defstate start-net-asset
                :start 0M)

(mount/defstate net-asset
                :start 0M)

(mount/defstate sell-point
                :start {:down-times-least 1M
                        :down-price-least -0.7M})

(mount/defstate reset-all
                :start false)

(mount/defstate start-price
                :start 0M)

(defn init
  "init state"
  []
  (let [now (utils/get-readable-time (System/currentTimeMillis) "yyyy-MM-dd_HH-mm-ss")]
    (when-not (.exists (clojure.java.io/as-file history-dir))
      (log/error "history dir:" history-dir "NOT EXISTS!")
      false)
    (when-not (.exists (clojure.java.io/as-file events/events-dir))
      (log/error "events dir:" events/events-dir "NOT EXISTS!")
      false)
    (mount/start-with {#'history-log-file (str history-dir "/klines.log." now)})
    (log/debug "klines history log:" history-log-file)
    (.createNewFile (clojure.java.io/as-file history-log-file))
    (mount/start-with {#'events/events-log-file (str events/events-dir "/events.log." now)})
    (log/debug "events log:" events/events-log-file)
    (.createNewFile (clojure.java.io/as-file events/events-log-file)))
  (events/reset-wallet)
  (mount/start-with {#'start-net-asset (bigdec (:net_asset (utils/get-account-info events/huobi-access-key
                                                                                           events/huobi-secret-key)))})
  (mount/start-with {#'start-price (:last (utils/get-staticmarket))})
  (mount/start-with {#'reset-all false}))

(defn chance-watcher
  "chance watcher"
  []
  (try
    (when (and reset-all
               (= status "cny"))
      (init))
    (let [kline kline
          lastest-kline (last kline)
          lastest-datetime (first lastest-kline)
          lastest-end-price (bigdec (nth lastest-kline 4))]
      (when (not= lastest-datetime last-check-datetime)
        (case status
          "cny" (let [re (da/down-up-point? kline
                                            (:down-times-least buy-point)
                                            (:down-price-least buy-point)
                                            (:up-times-least buy-point)
                                            (:up-price-least buy-point))]  ;; fixed history klines buy point
                  (when (:suitable? re)
                    (let [down-diff-price (:down-diff-price re)
                          up-diff-price (:up-diff-price re)]
                      (let [last-price (bigdec (:last (utils/get-staticmarket)))
                            diff-now (- last-price lastest-end-price)
                            lastest-top-price-diff (+ down-diff-price
                                                      up-diff-price
                                                      diff-now)
                            lastest-bottom-price-diff (+ up-diff-price diff-now)]
                        (when (and (<= lastest-top-price-diff -0.5M) ;; don't touch lastest top price
                                   (>= lastest-bottom-price-diff 0.5M)) ;; don't touch lastest bottom price
                          (events/balance-wallet)
                          (when (:success (events/show-hand "buy"))
                            (mount/start-with {#'status "btc"})
                            (mount/start-with {#'net-asset (bigdec (:net_asset (utils/get-account-info events/huobi-access-key
                                                                                                       events/huobi-secret-key)))})))))))
          "btc" (let [re (da/sell-point? kline
                                         (:down-times-least sell-point)
                                         (:down-price-least sell-point))  ;; fixed history klines sell point
                      net-asset-now (bigdec (:net_asset (utils/get-account-info events/huobi-access-key
                                                                                events/huobi-secret-key)))]
                  (when (> net-asset-now net-asset)
                    (mount/start-with {#'net-asset net-asset-now}))  ;; fix top price
                  (when (or (:suitable? re)
                            (<= (- net-asset-now net-asset) -3M))
                    (log/info "net asset diff:" (- net-asset-now net-asset))
                    (events/balance-wallet)
                    (when (:success (events/show-hand "sell"))
                      (mount/start-with {#'status "cny"})
                      (log/info "now net-asset-diff:" (- net-asset-now
                                                         start-net-asset)))))
          (throw (Exception. (str "status error: " status))))
        (mount/start-with {#'last-check-datetime lastest-datetime})))
    (catch Exception e
      (log/error "chance watcher ERROR:" e))))
