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
                        :btc nil
                        :loan-btc nil})

(mount/defstate last-loan-id
                :start "")

(mount/defstate event-types
                :start #{"buy" "sell" "loan" "repay"})

(defn event-model
  "buy, sell event model"
  [type time before-wallet after-wallet success?]
  (if (contains? event-types type)
    {:type type
     :before before-wallet
     :after after-wallet
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
                                      :btc (Float/parseFloat (:available_btc_display account-info))
                                      :loan-btc (Float/parseFloat (:loan_btc_display account-info))}})
      (log/info my-wallet))
    (catch Exception e
      (log/error e)
      (Thread/sleep 1000)
      (reset-wallet))))

(defn show-hand
  "buy or sell"
  [type]
  (when-not (contains? #{"buy" "sell"} type)
    (throw (Exception. (str "event type: " type " ERROR!"))))
  (let [before-wallet my-wallet]
    (try
      (let [re (case type
                 "buy" (utils/buy-market huobi-access-key huobi-secret-key (:cny before-wallet))
                 "sell" (utils/sell-market huobi-access-key huobi-secret-key (:btc before-wallet)))]
        (log/debug re)
        (Thread/sleep 3000)
        (reset-wallet)
        (if (= "success" (:result re))
          (do (utils/write-a-map (event-model type
                                              (utils/get-readable-time (System/currentTimeMillis))
                                              before-wallet
                                              my-wallet
                                              true)
                                 (str events-dir "/events.log"))
              {:info (str "id: " (:id re))
               :success true})
          (do (utils/write-a-map (event-model type
                                              (utils/get-readable-time (System/currentTimeMillis))
                                              before-wallet
                                              my-wallet
                                              false)
                                 (str events-dir "/events.log"))
              {:info re
               :success false})))
      (catch Exception e
        (log/error e)
        (reset-wallet)
        (utils/write-a-map (event-model type
                                        (utils/get-readable-time (System/currentTimeMillis))
                                        before-wallet
                                        my-wallet
                                        false)
                           (str events-dir "/events.log"))
        {:info (.toString e)
         :success false}))))

(defn lever-btc
  "loan or repay"
  [type]
  (when-not (contains? #{"loan" "repay"} type)
    (throw (Exception. (str "event type: " type " ERROR!"))))
  (let [before-wallet my-wallet]
    (try
      (let [re (case type
                 "loan" (utils/loan-btc huobi-access-key huobi-secret-key 1)
                 "repay" (utils/repay-btc huobi-access-key huobi-secret-key last-loan-id (:loan-btc before-wallet)))]
        (log/debug re)
        (Thread/sleep 3000)
        (reset-wallet)
        (if (= "success" (:result re))
          (do (utils/write-a-map (event-model type
                                              (utils/get-readable-time (System/currentTimeMillis))
                                              before-wallet
                                              my-wallet
                                              true)
                                 (str events-dir "/events.log"))
              (when (= type "loan")
                (mount/start-with {#'last-loan-id (:id re)})
                (log/info "loan id:" (:id re)))
              {:info (str "id: " (:id re))
               :success true})
          (do (utils/write-a-map (event-model type
                                              (utils/get-readable-time (System/currentTimeMillis))
                                              before-wallet
                                              my-wallet
                                              false)
                                 (str events-dir "/events.log"))
              {:info re
               :success false})))
      (catch Exception e
        (log/error e)
        (reset-wallet)
        (utils/write-a-map (event-model type
                                        (utils/get-readable-time (System/currentTimeMillis))
                                        before-wallet
                                        my-wallet
                                        false)
                           (str events-dir "/events.log"))
        {:info (.toString e)
         :success false}))))
