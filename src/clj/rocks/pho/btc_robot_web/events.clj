(ns rocks.pho.btc-robot-web.events
  (:require [clojure.tools.logging :as log]
            [mount.core :as mount]

            [rocks.pho.btc-robot-web.config :refer [env]]
            [rocks.pho.btc-robot-web.utils :as utils]))

(mount/defstate huobi-access-key
                :start (:access-key (:huobi env)))

(mount/defstate huobi-secret-key
                :start (:secret-key (:huobi env)))

(mount/defstate my-wallet
                :start {:cny nil
                        :btc nil})

(defn reset-wallet
  []
  (let [account-info (utils/get-account-info huobi-access-key
                                             huobi-secret-key)]
    (mount/start-with {#'my-wallet {:cny (:available_cny_display account-info)
                                    :btc (:available_btc_display account-info)}})
    (log/info my-wallet)))
