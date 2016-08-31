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

(mount/defstate klines
  :start (list))

(mount/defstate status
  :start "cny")

(mount/defstate last-check-datetime
  :start "")

(mount/defstate last-kline-log-datetime
  :start "")

(mount/defstate kline-watch-times :start 0)

(mount/defstate last-deal-buy-time
                :start "")

(mount/defstate last-kline-watcher-time :start "")

(defn kline-watcher
  "kline watcher"
  []
  (try
    (mount/start-with {#'kline-watch-times (inc kline-watch-times)})
    (when (= (mod kline-watch-times 600)
             0)
      (log/info "kline watch times:" kline-watch-times))
    (let [fixed-klines (drop-last (utils/get-kline "001"))
          found-index (utils/find-kline-datetime fixed-klines
                                           last-kline-log-datetime)
          new-klines (nthrest fixed-klines (inc found-index))]
      (when-not (empty? new-klines)
        (doseq [kline new-klines]
          (utils/write-a-object kline history-log-file))
        (mount/start-with {#'klines fixed-klines})
        (mount/start-with {#'last-kline-log-datetime (first (last fixed-klines))})))
    (mount/start-with {#'last-kline-watcher-time (utils/get-readable-time (System/currentTimeMillis))})
    (catch Exception e
      (log/error "kline watcher ERROR:" e))))

(mount/defstate down-up-point
  :start {:down-times-least 3M
          :down-price-least -1M
          :up-times-least 1M
          :up-price-least 0.7M})

(mount/defstate up-point
  :start {:up-times-least 1M
          :up-price-least 1M})

(mount/defstate start-net-asset
  :start 0M)

(mount/defstate net-asset
  :start 0M)

(mount/defstate down-point
  :start {:down-times-least 1M
          :down-price-least -0.7M})

(mount/defstate down-net-asset-baseline
  :start -3M)

(mount/defstate reset-all
  :start (true? false))

(mount/defstate start-price
  :start 0M)

(mount/defstate deal-times-one-round :start 10)

(mount/defstate rounds :start (list))

(defn init
  "init state"
  []
  (try
    (log/info "start init!")
    ;; init log file, klines events
    (let [now (utils/get-readable-time (System/currentTimeMillis) "yyyy-MM-dd_HH-mm-ss")]
      (when-not (.exists (clojure.java.io/as-file history-dir))
        (log/error "history dir:" history-dir "NOT EXISTS!"))
      (when-not (.exists (clojure.java.io/as-file events/events-dir))
        (log/error "events dir:" events/events-dir "NOT EXISTS!"))
      (mount/start-with {#'history-log-file (str history-dir "/klines.log." now)})
      (log/debug "klines history log:" history-log-file)
      (.createNewFile (clojure.java.io/as-file history-log-file))
      (mount/start-with {#'events/events-log-file (str events/events-dir "/events.log." now)})
      (log/debug "events log:" events/events-log-file)
      (.createNewFile (clojure.java.io/as-file events/events-log-file)))
    ;; reset wallet
    (events/reset-wallet)
    (mount/start-with {#'start-net-asset (bigdec (:net-asset events/my-wallet))})
    (mount/start-with {#'start-price (:last (utils/get-staticmarket))})
    (if (> (:btc events/my-wallet) 0.0)
      (mount/start-with {#'status "btc"})
      (mount/start-with {#'status "cny"}))
    ;; init conf
    (let [conf-file (str history-dir "/conf.json")]
      (when (.exists (clojure.java.io/as-file conf-file))
        (let [conf (last (utils/read-a-json-file conf-file))
              down-up-point-conf (:down-up-point conf)
              up-point-conf (:up-point conf)
              down-point-conf (:down-point conf)
              down-net-asset-baseline-conf (:down-net-asset-baseline conf)
              deal-times-one-round (:deal-times-one-round conf)]
          (log/info "conf:" conf)
          (mount/start-with {#'down-up-point (bigdec down-up-point-conf)})
          (mount/start-with {#'up-point (bigdec up-point-conf)})
          (mount/start-with {#'down-point (bigdec down-point-conf)})
          (mount/start-with {#'down-net-asset-baseline (bigdec down-net-asset-baseline-conf)})
          (mount/start-with {#'deal-times-one-round deal-times-one-round}))))
    ;; reset flag
    (mount/start-with {#'reset-all (true? false)})
    ;; reset events
    (mount/start-with {#'events/events (vec (list))})
    (log/info "end init!")
    (catch Exception e
      (log/error "init ERROR:" e)
      (Thread/sleep 1000)
      (init))))

(defn close
  "close one round"
  []
  (try
    (log/info "start close!")
    ;; show hand
    (when (> (:btc events/my-wallet)
             0M)
      (log/info "before closing show hand")
      (events/show-hand "sell"))
    ;; write conf
    (let [conf-file (str history-dir "/conf.json")]
      (utils/write-a-object {:down-up-point down-up-point
                             :up-point up-point
                             :down-point down-point
                             :down-net-asset-baseline down-net-asset-baseline
                             :deal-times-one-round deal-times-one-round
                             :datetime (utils/get-readable-time (System/currentTimeMillis))}
                            conf-file))
    (log/info "end close!")
    (catch Exception e
      (log/error "close ERROR:" e)
      (Thread/sleep 200)
      (close))))

(mount/defstate chance-watch-times :start 0)

(mount/defstate last-chance-watcher-time :start "")

(defn chance-watcher
  "chance watcher"
  []
  (try
    (mount/start-with {#'chance-watch-times (inc chance-watch-times)})
    (when (= (mod chance-watch-times 3000)
             0)
      (log/info "chance watch times:" chance-watch-times))
    (when reset-all
      (close)
      (init))
    (let [klines klines
          lastest-kline (last klines)
          lastest-datetime (first lastest-kline)
          lastest-end-price (bigdec (nth lastest-kline 4))]
      (when (not= lastest-datetime last-check-datetime)
        (case status
          "cny" (let [down-up-re (da/down-up-point? klines
                                                    (:down-times-least down-up-point)
                                                    (:down-price-least down-up-point)
                                                    (:up-times-least down-up-point)
                                                    (:up-price-least down-up-point))  ;; fixed history klines down up point
                      down-up-buy? (atom false)
                      up-buy? (atom false)]
                  (when (:suitable? down-up-re)
                    (let [down-diff-price (:down-diff-price down-up-re)
                          up-diff-price (:up-diff-price down-up-re)]
                      (let [last-price (bigdec (:last (utils/get-staticmarket)))
                            diff-now (- last-price lastest-end-price)
                            lastest-top-price-diff (+ down-diff-price
                                                      up-diff-price
                                                      diff-now)
                            lastest-bottom-price-diff (+ up-diff-price diff-now)]
                        (if (and (<= lastest-top-price-diff -0.5M) ;; don't touch lastest top price
                                 (>= lastest-bottom-price-diff 0.5M)) ;; don't touch lastest bottom price
                          (do (reset! down-up-buy? true)
                              (log/info "D-U-BUY"))
                          (log/info "now price:" last-price "not in +- 0.5 range."
                                    "lastest top price diff:" lastest-top-price-diff
                                    "lastest bottom price diff:" lastest-bottom-price-diff)))))
                  (when-not @down-up-buy?
                    (let [up-re (da/up-point? klines
                                              (:up-times-least up-point)
                                              (:up-price-least up-point))]
                      (when (:suitable? up-re)
                        (let [last-price (bigdec (:last (utils/get-staticmarket)))]
                          (if (> last-price (:end-price up-re))  ;; up history and up now
                            (do (reset! up-buy? true)
                                (log/info "U-BUY"))
                            (log/info "now price:" last-price "down than last end price:" (:end-price up-re)))))))
                  (when (or @down-up-buy?
                            @up-buy?)
                    (events/balance-wallet)
                    (when (:success (events/show-hand "buy"))
                      (mount/start-with {#'status "btc"})
                      (mount/start-with {#'net-asset (bigdec (:net_asset (utils/get-account-info events/huobi-access-key
                                                                                                 events/huobi-secret-key)))}))))
          "btc" (let [re (da/down-point? klines
                                         (:down-times-least down-point)
                                         (:down-price-least down-point))  ;; fixed history klines sell point
                      net-asset-now (bigdec (:net_asset (utils/get-account-info events/huobi-access-key
                                                                                events/huobi-secret-key)))
                      history-sell? (atom false)
                      net-asset-sell? (atom false)]
                  (when (> net-asset-now net-asset)
                    (mount/start-with {#'net-asset net-asset-now}))  ;; fix top price
                  (when (:suitable? re)
                    (let [last-end-price (:end-price re)
                          last-price (bigdec (:last (utils/get-staticmarket)))]
                      (if (< last-price last-end-price)  ;; down history & down now
                        (do (reset! history-sell? true)
                            (log/info "H-SELL"))
                        (log/info "last price:" last-price "up than last end price:" last-end-price))))
                  (when-not @history-sell?
                    (if (<= (- net-asset-now net-asset) down-net-asset-baseline)  ;;  net asset down
                      (do (reset! net-asset-sell? true)
                          (log/info "N-SELL"))
                      (log/info "history top net asset:" net-asset "net asset down lower than" down-net-asset-baseline)))
                  (when (or @history-sell?
                            @net-asset-sell?)
                    (events/balance-wallet)
                    (when (:success (events/show-hand "sell"))
                      (mount/start-with {#'status "cny"})
                      (let [cleaned-events (da/clean-events events/events)
                            deals (when-not (empty? cleaned-events)
                                    (da/events-analysis cleaned-events))]
                        (when (and (not (nil? deals))
                                   (>= (.size deals) deal-times-one-round))
                          (mount/start-with {#'reset-all true}))))))
          (throw (Exception. (str "status error: " status))))
        (mount/start-with {#'last-check-datetime lastest-datetime})))
    (mount/start-with {#'last-chance-watcher-time (utils/get-readable-time (System/currentTimeMillis))})
    (catch Exception e
      (log/error "chance watcher ERROR:" e))))
