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
                :start (:events-dir (:btc-robot env)))

(mount/defstate my-wallet
                :start {:cny nil
                        :btc nil})

(mount/defstate event-types :start #{"buy" "sell"})

(defn event-model
  "buy, sell event model"
  [type time before-money before-btc after-money after-btc success?]
  (if (contains? event-types type)
    {:type type
     :before {:money before-money
              :btc before-btc}
     :after {:money after-money
             :btc after-btc}
     :time time
     :success success?}
    (throw (Exception. (str "event type: " type " ERROR!")))))

(defn reset-wallet
  []
  (try
    (let [account-info (utils/get-account-info huobi-access-key
                                               huobi-secret-key)]
      (log/debug account-info)
      (mount/start-with {#'my-wallet {:cny (Float/parseFloat (:available_cny_display account-info))
                                      :btc (Float/parseFloat (:available_btc_display account-info))}})
      (log/info my-wallet))
    (catch Exception e
      (log/error e)
      (Thread/sleep 1000)
      (reset-wallet))))

(defn show-hand
  "buy or sell"
  [type]
  (when-not (contains? event-types type)
    (throw (Exception. (str "event type: " type " ERROR!"))))
  (let [before {:money (:cny my-wallet)
                :btc (:btc my-wallet)}]
    (try
      (let [re (case type
                 "buy" (utils/buy-market huobi-access-key huobi-secret-key (:money before))
                 "sell" (utils/sell-market huobi-access-key huobi-secret-key (:btc before)))]
        (log/debug re)
        (Thread/sleep 3000)
        (reset-wallet)
        (if (= "success" (:result re))
          (do (utils/write-a-map (event-model type
                                              (utils/get-readable-time (System/currentTimeMillis))
                                              (:money before)
                                              (:btc before)
                                              (:cny my-wallet)
                                              (:btc my-wallet)
                                              true)
                                 (str events-dir "/events.log"))
              {:id (:id re)
             :success true})
          (do (utils/write-a-map (event-model type
                                              (utils/get-readable-time (System/currentTimeMillis))
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
        (utils/write-a-map (event-model type
                                        (utils/get-readable-time (System/currentTimeMillis))
                                        (:money before)
                                        (:btc before)
                                        (:cny my-wallet)
                                        (:btc my-wallet)
                                        false)
                           (str events-dir "/events.log"))
        {:info (.toString e)
         :success false}))))
