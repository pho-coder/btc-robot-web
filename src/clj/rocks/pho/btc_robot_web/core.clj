(ns rocks.pho.btc-robot-web.core
  (:require [luminus.repl-server :as repl]
            [luminus.http-server :as http]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.tools.logging :as log]
            [luminus.logger :as logger]
            [mount.core :as mount]

            [rocks.pho.btc-robot-web.handler :as handler]
            [rocks.pho.btc-robot-web.config :refer [env]]
            [rocks.pho.btc-robot-web.events :as events])
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
  (log/info "events dir:" events/events-dir)
  (events/reset-wallet)
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app)))

(defn -main [& args]
  (start-app args))
