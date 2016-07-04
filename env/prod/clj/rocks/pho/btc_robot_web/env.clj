(ns rocks.pho.btc-robot-web.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[rocks.pho.btc-robot-web started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[rocks.pho.btc-robot-web has shut down successfully]=-"))
   :middleware identity})
