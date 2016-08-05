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

(mount/defstate events-log-file
                :start "")

(mount/defstate my-wallet
                :start {:cny nil
                        :btc nil
                        :loan-btc nil})

(mount/defstate last-loan-id
                :start "")

(mount/defstate event-types
                :start #{"buy" "sell" "loan" "repay" "balance"})

(defn event-model
  "buy, sell event model"
  [type before-wallet after-wallet success?]
  (if (contains? event-types type)
    {:type type
     :before before-wallet
     :after after-wallet
     :time (utils/get-readable-time (System/currentTimeMillis))
     :success success?}
    (throw (Exception. (str "event type: " type " ERROR!")))))

(defn reset-wallet
  []
  (try
    (let [account-info (utils/get-account-info huobi-access-key
                                               huobi-secret-key)]
      (log/debug account-info)
      (when (= (:code account-info) "62")
        (throw (Exception. (str account-info))))
      (mount/start-with {#'my-wallet {:cny (Float/parseFloat (:available_cny_display account-info))
                                      :btc (Float/parseFloat (:available_btc_display account-info))
                                      :loan-btc (Float/parseFloat (:loan_btc_display account-info))
                                      :net-asset (Float/parseFloat (:net_asset account-info))}})
      (log/info my-wallet))
    (catch Exception e
      (log/error e)
      (Thread/sleep 1000)
      (reset-wallet))))

(defn log-event
  [event]
  (log/info event)
  (utils/write-a-object event events-log-file))

(defn balance-wallet
  []
  (try
    (let [before-wallet my-wallet
          account-info (utils/get-account-info huobi-access-key
                                               huobi-secret-key)
          cny (Float/parseFloat (:available_cny_display account-info))
          btc (Float/parseFloat (:available_btc_display account-info))
          loan-btc (Float/parseFloat (:loan_btc_display account-info))]
      (if (or (not= cny (:cny before-wallet))
              (not= btc (:btc before-wallet))
              (not= loan-btc (:loan-btc before-wallet)))
        (do (mount/start-with {#'my-wallet {:cny cny
                                            :btc btc
                                            :loan-btc loan-btc}})
            (log-event (event-model "balance"
                                    before-wallet
                                    {:cny cny
                                     :btc btc
                                     :loan-btc loan-btc}
                                    true))
            (log/info "balance wallet: " my-wallet))))
    (catch Exception e
      (log/error "balance wallet:" e)
      (Thread/sleep 1000)
      (balance-wallet))))

(defn one-deal
  "buy or sell by amount & price"
  [type amount price]
  (when-not (contains? #{"buy" "sell"} type)
    (throw (Exception. (str "event type: " type " ERROR!"))))
  (let [before-wallet my-wallet]
    (try
      (let [re (case type
                 "buy" (utils/buy huobi-access-key
                                  huobi-secret-key
                                  amount
                                  price)
                 "sell" (utils/sell huobi-access-key
                                    huobi-secret-key
                                    amount
                                    price))]
        (log/debug re)
        (Thread/sleep 2000)
        (reset-wallet)
        (if (= "success" (:result re))
          (do (log-event (event-model type
                                      before-wallet
                                      my-wallet
                                      true))
              {:info (str "id: " (:id re))
               :success true})
          (do (log-event (event-model type
                                      before-wallet
                                      my-wallet
                                      false))
              {:info re
               :success false})))
      (catch Exception e
        (log/error e)
        (reset-wallet)
        (log-event (event-model type
                                before-wallet
                                my-wallet
                                false))
        {:info (.toString e)
         :success false}))))

(defn show-hand
  "buy or sell all"
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
          (do (log-event (event-model type
                                      before-wallet
                                      my-wallet
                                      true))
              {:info (str "id: " (:id re))
               :success true})
          (do (log-event (event-model type
                                      before-wallet
                                      my-wallet
                                      false))
              {:info re
               :success false})))
      (catch Exception e
        (log/error e)
        (reset-wallet)
        (log-event (event-model type
                                before-wallet
                                my-wallet
                                false))
        {:info (.toString e)
         :success false}))))

(defn lever-btc
  "loan or repay"
  [type amount]
  (when-not (contains? #{"loan" "repay"} type)
    (throw (Exception. (str "event type: " type " ERROR!"))))
  (let [before-wallet my-wallet]
    (try
      (let [re (case type
                 "loan" (utils/loan-btc huobi-access-key huobi-secret-key amount)
                 "repay" (utils/repay-btc huobi-access-key huobi-secret-key last-loan-id amount))]
        (log/debug re)
        (Thread/sleep 3000)
        (reset-wallet)
        (if (= "success" (:result re))
          (do (log-event (event-model type
                                      before-wallet
                                      my-wallet
                                      true))
              (when (= type "loan")
                (mount/start-with {#'last-loan-id (:id re)})
                (log/info "loan id:" (:id re)))
              {:info (str "id: " (:id re))
               :success true})
          (do (log-event (event-model type
                                      before-wallet
                                      my-wallet
                                      false))
              {:info re
               :success false})))
      (catch Exception e
        (log/error e)
        (reset-wallet)
        (log-event (event-model type
                                before-wallet
                                my-wallet
                                false))
        {:info (.toString e)
         :success false}))))
