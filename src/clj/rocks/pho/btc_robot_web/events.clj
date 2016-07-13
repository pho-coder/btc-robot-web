(ns rocks.pho.btc-robot-web.events
  (:require [clojure.tools.logging :as log]
            [mount.core :as mount]

            [rocks.pho.btc-robot-web.config :refer [env]]
            [rocks.pho.btc-robot-web.utils :as utils]))

(mount/defstate huobi-access-key
                :start (:access-key (:huobi env)))

(mount/defstate huobi-secret-key
                :start (:secret-key (:huobi env)))

(mount/defstate events-dir
                :start (:events-dir (:btc-robot-web env)))

(mount/defstate my-wallet
                :start {:cny nil
                        :btc nil})

(defn buy-event-model
 "buy event model"
  [time before-money before-btc after-money after-btc success?]
  {:type "buy"
   :before {:money before-money
            :btc before-btc}
   :after {:money after-money
           :btc after-btc}
   :time time
   :success success?})

(defn sell-event-model
  "sell event model"
  [time before-money before-btc after-money after-btc success?]
  {:type "sell"
   :before {:money before-money
            :btc before-btc}
   :after {:money after-money
           :btc after-btc}
   :time time
   :success success?})

(defn reset-wallet
  []
  (try
    (let [account-info (utils/get-account-info huobi-access-key
                                               huobi-secret-key)]
      (mount/start-with {#'my-wallet {:cny (:available_cny_display account-info)
                                      :btc (:available_btc_display account-info)}})
      (log/info my-wallet))
    (catch Exception e
      (log/error e)
      (Thread/sleep 1000)
      (reset-wallet))))

(defn buy-all
  []
  (let [before {:money (:cny my-wallet)
                :btc (:btc my-wallet)}]
    (try
      (let [_ (log/debug (int (:cny (:money before))))
            re (utils/buy-market huobi-access-key huobi-secret-key (int (:cny (:money before))))]
        (log/debug re)
        (reset-wallet)
        (if (= "success" (:result re))
          (do (utils/write-a-map (buy-event-model (utils/get-readable-time (System/currentTimeMillis))
                                                  (:money before)
                                                  (:btc before)
                                                  (:cny my-wallet)
                                                  (:btc my-wallet)
                                                  true)
                                 (str events-dir "/events.log"))
            {:id (:id re)
             :success true})
          (do (utils/write-a-map (buy-event-model (utils/get-readable-time (System/currentTimeMillis))
                                                  (:money before)
                                                  (:btc before)
                                                  (:cny my-wallet)
                                                  (:btc my-wallet)
                                                  false)
                                 (str events-dir "/events.log"))
              {:info re
               :success false})))
      (catch Exception e
        (log/error e)
        (reset-wallet)
        (utils/write-a-map (buy-event-model (utils/get-readable-time (System/currentTimeMillis))
                                            (:money before)
                                            (:btc before)
                                            (:cny my-wallet)
                                            (:btc my-wallet)
                                            false)
                           (str events-dir "/events.log"))
        {:info (.toString e)
         :success false}))))
