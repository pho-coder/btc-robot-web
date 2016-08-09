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
            [rocks.pho.btc-robot-web.watcher :as watcher])
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
  (watcher/close)
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
  (watcher/init)
  (timer/schedule-recurring kline-timer 1 17 watcher/kline-watcher)
  (timer/schedule-recurring chance-timer 7 3 watcher/chance-watcher)
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))

(defn -main [& args]
  (start-app args))
