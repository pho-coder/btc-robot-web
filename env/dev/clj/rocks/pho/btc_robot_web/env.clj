(ns rocks.pho.btc-robot-web.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [rocks.pho.btc-robot-web.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[rocks.pho.btc-robot-web started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[rocks.pho.btc-robot-web has shut down successfully]=-"))
   :middleware wrap-dev})
