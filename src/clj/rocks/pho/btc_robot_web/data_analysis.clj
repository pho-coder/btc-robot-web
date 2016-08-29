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

(defn clean-events
  "clean events: cut head, tail, failed, no buy or sell"
  [events]
  (let [cleaned-events (filter #(and (:success %)
                                     (or (= "buy" (:type %))
                                         (= "sell" (:type %)))) events)
        cutted-head (loop [events cleaned-events]
                      (if (or (= "buy"
                                 (:type (first events)))
                              (empty? events))
                        events
                        (recur (rest events))))]
    (loop [events cutted-head]
      (if (or (= "sell"
                 (:type (last events)))
              (empty? events))
        events
        (recur (drop-last events))))))

(defn events-analysis
  "events analysis"
  [cleaned-events]
  (let [first-buy (first cleaned-events)
        first-buy-time (:time first-buy)
        first-buy-cny (bigdec (:cny (:before first-buy)))
        last-sell (last cleaned-events)
        last-sell-time (:time last-sell)
        last-sell-cny (bigdec (:cny (:after last-sell)))
        these-deals (loop [events cleaned-events
                           deals (vec (list))
                           buy nil]
                      (if (empty? events)
                        deals
                        (let [this (first events)
                              type (:type this)]
                          (case type
                            "buy" (recur (rest events)
                                         deals
                                         this)
                            "sell" (let [buy-cny (bigdec (:cny (:before buy)))
                                         buy-avg-price (with-precision 2 (/ (- (:cny (:before buy))
                                                                               (:cny (:after buy)))
                                                                            (- (:btc (:after buy))
                                                                               (:btc (:before buy)))))
                                         sell-cny (bigdec (:cny (:after this)))
                                         sell-avg-price (with-precision 2 (/ (- (:cny (:after this))
                                                                                (:cny (:before this)))
                                                                             (- (:btc (:before this))
                                                                                (:btc (:after this)))))
                                         diff-cny (- sell-cny buy-cny)]
                                     (recur (rest events)
                                            (conj deals {:buy-time (:time buy)
                                                         :sell-time (:time this)
                                                         :buy-cny buy-cny
                                                         :buy-avg-price buy-avg-price
                                                         :sell-cny sell-cny
                                                         :sell-avg-price sell-avg-price
                                                         :diff-cny diff-cny
                                                         :good? (if (> diff-cny 0)
                                                                  true
                                                                  false)})
                                            nil))
                            (throw (Exception. (str "event type: " type " ERROR!")))))))]
    {:first-buy-time first-buy-time
     :last-sell-time last-sell-time
     :first-buy-cny first-buy-cny
     :last-sell-cny last-sell-cny
     :diff-cny (- last-sell-cny first-buy-cny)
     :deals these-deals}))

(defn deal-point-analysis
  "get a deal & klines after the deal, the line diff good normal bad, the line is pos num"
  [type deal klines the-line]
  (let [deal-price (case type
                     :buy (:buy-avg-price deal)
                     :sell (:sell-avg-price deal)
                     (throw (Exception. (str "type: " type " ERROR!"))))]
    (loop [ks klines
           re {:good-price-times 0
               :bad-price-times 0
               :max-good-diff-price 0M
               :max-bad-diff-price 0M}]
      (if (empty? ks)
        re
        (let [kline (first ks)
              mid-price (:mid-price kline)
              diff-price (- mid-price
                            deal-price)]
          (cond
            (>= diff-price
                the-line) (if (> diff-price
                                 ((case type
                                   :buy :max-good-diff-price
                                   :sell :max-bad-diff-price) re))
                            (recur (rest ks)
                                   (update-in
                                    (assoc-in re
                                              [(case type
                                                 :buy :max-good-diff-price
                                                 :sell :max-bad-diff-price)]
                                              diff-price)
                                    [(case type
                                       :buy :good-price-times
                                       :sell :bad-price-times)]
                                    inc))
                            (recur (rest ks)
                                   (update-in
                                    re
                                    [(case type
                                       :buy :good-price-times
                                       :sell :bad-price-times)]
                                    inc)))
            (<= diff-price
                (unchecked-negate-int the-line)) (if (< diff-price
                                                        ((case type
                                                           :buy :max-bad-diff-price
                                                           :sell :max-good-diff-price) re))
                                                   (recur (rest ks)
                                                          (update-in
                                                           (assoc-in re
                                                                     [(case type
                                                                        :buy :max-bad-diff-price
                                                                        :sell :max-good-diff-price)]
                                                                     diff-price)
                                                           [(case type
                                                              :buy :bad-price-times
                                                              :sell :good-price-times)]
                                                           inc))
                                                   (recur (rest ks)
                                                          (update-in
                                                           re
                                                           [(case type
                                                              :buy :bad-price-times
                                                              :sell :good-price-times)]
                                                           inc)))
            :else (recur (rest ks)
                         re)))))))

(defn deals-analysis
  "get a list deals statistics data & analysis by klines"
  [deals klines]
  (let [num (.size deals)
        first-deal (first deals)
        last-deal (last deals)
        start-time (:buy-time first-deal)
        start-datetime (.substring start-time 0 16)
        start-cny (:buy-cny first-deal)
        end-time (:sell-time last-deal)
        end-datetime (.substring end-time 0 16)
        end-cny (:sell-cny last-deal)
        ;; add buy-point-best? sell-point-best?
        the-diff-line 0.5M]))
    
    

(defn up-point?
  [a-kline up-times-least & [up-price-least]]
  (let [re (recently-continued-times a-kline)]
    (if (and (= "up" (:trend re))
             (>= (:times re) up-times-least)
             (>= (:diff-price re) (or up-price-least 0)))
      (do (log/info "up re:" re)
          {:suitable? true
           :up-diff-price (:diff-price re)
           :end-price (:end-price re)})
      {:suitable? false})))

(defn down-point?
  [a-kline down-times-least & [down-price-least]]
  (let [re (recently-continued-times a-kline)]
    (if (and (= "down" (:trend re))
             (>= (:times re) down-times-least)
             (<= (:diff-price re) (or down-price-least 0)))
      (do (log/info "down re:" re)
          {:suitable? true
           :down-diff-price (:diff-price re)
           :end-price (:end-price re)})
      {:suitable? false})))

(defn down-up-point?
  "a-kline : a kline
   down-times-least : down times at least
   down-price-least : down price diff at least
   up-times-least : up times at least
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
            (do (log/info "down re:" re1)
                (log/info "up re:" re)
                {:suitable? true
                 :down-diff-price (:diff-price re1)
                 :up-diff-price (:diff-price re)})
            {:suitable? false}))
        {:suitable? false})
      {:suitable? false})))

(defn analysis-a-history-kline-normal
  [a-history-kline times]
  (let [size (.size a-history-kline)]
    (loop [index 1
           status "cny"]
      (if (<= index size)
        (let [a-kline (take index a-history-kline)
              re (recently-continued-times a-kline)]
          (cond
            (up-point? re times) (if (= status "cny")
                                    (do (prn "buy point: " re)
                                        (recur (inc index) "btc"))
                                    (recur (inc index) status))
            (down-point? re times) (if (= status "btc")
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
            (down-point? a-kline 1 -0.7)
            (if (= status "btc")
              (do (prn "sell point: " re)
                  (recur (inc index) "cny"))
              (recur (inc index) status))
            :else (recur (inc index) status)))))))
