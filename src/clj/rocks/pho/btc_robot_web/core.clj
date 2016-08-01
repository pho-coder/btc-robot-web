(ns rocks.pho.btc-robot-web.core
  (:require [luminus.repl-server :as repl]
            [luminus.http-server :as http]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [luminus.logger :as logger]
            [mount.core :as mount]
            [com.jd.bdp.magpie.util.timer :as timer]

            [rocks.pho.btc-robot-web.handler :as handler]
            [rocks.pho.btc-robot-web.config :refer [env]]
            [rocks.pho.btc-robot-web.events :as events]
            [rocks.pho.btc-robot-web.watcher :as watcher]
            [rocks.pho.btc-robot-web.utils :as utils])
  (:gen-class))

(def cli-options
  [["-p" "--port PORT" "Port number"
    :parse-fn #(Integer/parseInt %)]])

(mount/defstate ^{:on-reload :noop}
                http-server
                :start
                (http/start
                  (-> env
                      (assoc :handler (handler/app))
                      (update :port #(or (-> env :options :port) %))))
                :stop
                (http/stop http-server))

(mount/defstate ^{:on-reload :noop}
                repl-server
                :start
                (when-let [nrepl-port (env :nrepl-port)]
                  (repl/start {:port nrepl-port}))
                :stop
                (when repl-server
                  (repl/stop repl-server)))

(mount/defstate log
                :start (logger/init (:log-config env)))

(mount/defstate ^{:on-reload :noop}
                chance-timer
                :start
                (timer/mk-timer)
                :stop
                (timer/cancel-timer chance-timer))

(mount/defstate ^{:on-reload :noop}
                kline-timer
                :start
                (timer/mk-timer)
                :stop
                (timer/cancel-timer kline-timer))

(defn stop-app []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped"))
  (shutdown-agents))

(defn start-app [args]
  (doseq [component (-> args
                        (parse-opts cli-options)
                        mount/start-with-args
                        :started)]
    (log/info component "started"))
  (log/debug env)
  (log/debug "access key:" events/huobi-access-key)
  (log/debug "secret key:" events/huobi-secret-key)
  (let [now (utils/get-readable-time (System/currentTimeMillis) "yyyy-MM-dd_HH-mm-ss")]
    (when-not (.exists (clojure.java.io/as-file watcher/history-dir))
      (log/error "history dir:" watcher/history-dir "NOT EXISTS!")
      (stop-app))
    (when-not (.exists (clojure.java.io/as-file events/events-dir))
      (log/error "events dir:" events/events-dir "NOT EXISTS!"))
    (mount/start-with {#'watcher/history-log-file (str watcher/history-dir "/klines.log." now)})
    (log/debug "klines history log:" watcher/history-log-file)
    (.createNewFile (clojure.java.io/as-file watcher/history-log-file))
    (mount/start-with {#'events/events-log-file (str events/events-dir "/events.log." now)})
    (log/debug "events log:" events/events-log-file)
    (.createNewFile (clojure.java.io/as-file events/events-log-file)))
  (events/reset-wallet)
  (mount/start-with {#'watcher/start-net-asset (bigdec (:net_asset (utils/get-account-info events/huobi-access-key
                                                                                           events/huobi-secret-key)))})
  (timer/schedule-recurring kline-timer 1 17 watcher/kline-watcher)
  (timer/schedule-recurring chance-timer 7 3 watcher/chance-watcher)
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))

(defn -main [& args]
  (start-app args))
