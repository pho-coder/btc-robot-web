(ns rocks.pho.btc-robot-web.data-analysis
  (:require [rocks.pho.btc-robot-web.utils :as utils]))

(defn recently-continued-times
  "recently continured times
  input : a kline from api
  return:
  {:trend \"up\" or \"down\"
   :times 10
   :datetime \"2016-07-01 13:13\" - lastest time
   :start-price 12.2M
   :end-price 13.2M}"
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
                    :end-price (:end-price last-kline)})
            (let [trend (trend? (:trend r)
                                (:trend last-kline))]
              (if (= trend "other")
                r
                (recur (pop kls)
                       {:trend trend
                        :times (inc (:times r))
                        :end-time (:datetime r)
                        :start-time (:datetime last-kline)
                        :start-price (:start-price last-kline)
                        :end-price (:end-price r)})))))))))

(defn buy-point?
  [a-kline times]
  (let [re (recently-continued-times a-kline)]
    (if (and (= "up" (:trend re))
             (>= (:times re) times))
      true
      false)))

(defn sell-point?
  [a-kline times]
  (let [re (recently-continued-times a-kline)]
    (if (and (= "down" (:trend re))
             (>= (:times re) times))
      true
      false)))

(defn down-up-point?
  [a-kline up-times-top down-times-low]
  (let [re (recently-continued-times a-kline)]
    (if (and (= "up" (:trend re))
             (<= (:times re) up-times-top))
      (if (>= (- (.size a-kline) (:times re))
              down-times-low)
        (let [re (recently-continued-times (take (- (.size a-kline) (:times re))
                                                 a-kline))]
          (if (and (= "down" (:trend re))
                   (>= (:times re) down-times-low))
            ))
        false)
      false)))

(defn simulate-a-history-kline
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
