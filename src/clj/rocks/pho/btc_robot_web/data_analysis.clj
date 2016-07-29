(ns rocks.pho.btc-robot-web.data-analysis
  (:require [clojure.tools.logging :as log]

            [rocks.pho.btc-robot-web.utils :as utils]))

(defn recently-continued-times
  "recently continured times
  input : a kline from api
  return:
  {:trend \"up\" or \"down\"
   :times 10
   :start-time \"2016-07-01 13:13\" - lastest time
   :start-price 12.2M
   :end-time 
   :end-price 13.2M
   :diff-price 1M}"
  [recently-klines]
  (let [klines (reverse (map #(utils/parse-kline-data %) recently-klines))
        trend? (fn [r-trend last-trend]
                 ;; up and down, down and up shows trend interrupt : other
                 ;; up or up, down or down shows trend continued : up or down
                 ;; others shows flat : flat
                 (cond
                   (or (and (= r-trend "up")
                            (= last-trend "down"))
                       (and (= r-trend "down")
                            (= last-trend "up"))) "other"
                   (or (= r-trend "up")
                       (= last-trend "up")) "up"
                   (or (= r-trend "down")
                       (= last-trend "down")) "down"
                   :else "flat"))]
    (loop [kls klines
           r {:trend "other"
              :times 0
              :end-time nil
              :end-price nil
              :start-time nil
              :start-price nil}]
      (if (empty? kls)
        r
        (let [last-kline (first kls)] 
          (if (= 0 (:times r))
            (recur (pop kls)
                   {:trend (:trend last-kline)
                    :times 1
                    :end-time (:datetime last-kline)
                    :start-time (:datetime last-kline)
                    :start-price (:start-price last-kline)
                    :end-price (:end-price last-kline)
                    :diff-price (- (:end-price last-kline)
                                   (:start-price last-kline))})
            (let [trend (trend? (:trend r)
                                (:trend last-kline))]
              (if (= trend "other")
                r
                (recur (pop kls)
                       {:trend trend
                        :times (inc (:times r))
                        :end-time (:end-time r)
                        :start-time (:datetime last-kline)
                        :start-price (:start-price last-kline)
                        :end-price (:end-price r)
                        :diff-price (- (:end-price r)
                                       (:start-price last-kline))})))))))))

(defn buy-point?
  [a-kline times]
  (let [re (recently-continued-times a-kline)]
    (if (and (= "up" (:trend re))
             (>= (:times re) times))
      (do (log/info "up re:" re)
        true)
      false)))

(defn sell-point?
  [a-kline down-times-least & [down-price-least]]
  (let [re (recently-continued-times a-kline)]
    (if (and (= "down" (:trend re))
             (>= (:times re) down-times-least)
             (<= (:diff-price re) (or down-price-least 0)))
      (do (log/info "down re:" re)
        true)
      false)))

(defn down-up-point?
  "a-kline : a kline
   down-times-least : down times at least
   down-price-least : down price diff at least
   up-times-most : up times at most
   up-price-least : up price diff at least"
  [a-kline down-times-least down-price-least up-times-least up-price-least]
  (let [re (recently-continued-times a-kline)]
    (if (and (= "up" (:trend re))
             (>= (:times re) up-times-least)
             (>= (:diff-price re) up-price-least))
      (if (>= (- (.size a-kline) (:times re))
              down-times-least)
        (let [re1 (recently-continued-times (drop-last (:times re)
                                                       a-kline))]
          (if (and (= "down" (:trend re1))
                   (>= (:times re1) down-times-least)
                   (<= (:diff-price re1) down-price-least))
            (do (log/info "up re:" re)
                (log/info "down re:" re1)
              true)
            false))
        false)
      false)))

(defn analysis-a-history-kline-normal
  [a-history-kline times]
  (let [size (.size a-history-kline)]
    (loop [index 1
           status "cny"]
      (if (<= index size)
        (let [a-kline (take index a-history-kline)
              re (recently-continued-times a-kline)]
          (cond
            (buy-point? re times) (if (= status "cny")
                                    (do (prn "buy point: " re)
                                        (recur (inc index) "btc"))
                                    (recur (inc index) status))
            (sell-point? re times) (if (= status "btc")
                                     (do (prn "sell point: " re)
                                         (recur (inc index) "cny"))
                                     (recur (inc index) status))
            :else (recur (inc index) status)))))))

(defn analysis-a-history-kline-down-up-point
  [a-history-kline down-times-least down-price-least up-times-least up-price-least]
  (let [size (.size a-history-kline)]
    (loop [index 1
           status "cny"]
      (if (<= index size)
        (let [a-kline (take index a-history-kline)
              re (recently-continued-times a-kline)
              re2 (recently-continued-times (drop-last (:times re)
                                                       a-kline))]
          (cond
            (down-up-point? a-kline down-times-least down-price-least up-times-least up-price-least)
            (if (= status "cny")
              (do (prn "buy point: " re re2)
                  (recur (inc index) "btc"))
              (recur (inc index) status))
            (sell-point? a-kline 1)
            (if (= status "btc")
              (do (prn "sell point: " re)
                  (recur (inc index) "cny"))
              (recur (inc index) status))
            :else (recur (inc index) status)))))))
