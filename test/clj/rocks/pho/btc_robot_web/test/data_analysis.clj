(ns rocks.pho.btc-robot-web.test.data-analysis
  (:require [clojure.test :refer :all]
            [rocks.pho.btc-robot-web.data-analysis :refer :all]))

(deftest test-recently-continued-times
  (testing "recently-continued-times"
    (let [test-down-klines [["20160725165900000" 4402.96 4403.05 4402.45 4402.9 148.8721] ["20160725170000000" 4402.9 4403.8 4402.8 4403 171.0096] ["20160725170100000" 4403 4403.36 4402.9 4403 117.8483] ["20160725170200000" 4403.05 4403.15 4402.32 4402.41 58.4977] ["20160725170300000" 4402.34 4402.4 4402.04 4402.04 1.9823]]
          test-down-re {:trend "down" :times 3 :start-price 4403M :end-price 4402.04M :datetime "2016-07-25 17:03"}
          test-up-klines [["20160725165900000" 4402.96 4403.05 4402.45 4402.9 148.8721] ["20160725170000000" 4401.9 4403.8 4402.8 4402 171.0096] ["20160725170100000" 4402 4403.36 4402.9 4402 117.8483] ["20160725170200000" 4402.05 4403.15 4402.32 4402.41 58.4977] ["20160725170300000" 4402.34 4402.4 4402.04 4403.04 1.9823]]
          test-up-re {:trend "up" :times 4 :start-price 4401.9M :end-price 4403.04M :datetime "2016-07-25 17:03"}]
      (is (= test-down-re (recently-continued-times test-down-klines)))
      (is (= test-up-re (recently-continued-times test-up-klines))))))

