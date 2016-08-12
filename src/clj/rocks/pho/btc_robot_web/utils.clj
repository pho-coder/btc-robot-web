(ns rocks.pho.btc-robot-web.utils
  (:require [clj-http.client :as http-client]
            [clojure.data.json :as json]
            [clj-time.coerce :as coerce-time]
            [clj-time.format :as format-time]
            [digest :as digest]))

(defn get-readable-time
  "get readable time default format yyyy-MM-dd HH:mm:ss"
  ([long-time fm]
   (let [t (coerce-time/from-long (* (condp = (type long-time)
                                       String (Long/parseLong long-time)
                                       Long long-time
                                       Integer long-time
                                       (throw (Exception. (str long-time " type: " (type long-time) " NOT Long or String!"))))
                                     (case (.length (str long-time))
                                       10 1000
                                       13 1
                                       (throw (Exception. (str long-time " length error!"))))))
         tz (clj-time.core/from-time-zone t (clj-time.core/time-zone-for-offset -8))
         f (format-time/formatter fm)]
     (format-time/unparse f tz)))
  ([long-time]
   (get-readable-time long-time "yyyy-MM-dd HH:mm:ss")))

(defn get-staticmarket
  "get realtime market"
  []
  (let [api-url "http://api.huobi.com/staticmarket/ticker_btc_json.js"]
    (let [staticmarket (json/read-str (:body (http-client/post api-url))
                                      :key-fn keyword)
          time (get-readable-time (:time staticmarket))
          ticker (:ticker staticmarket)
          open (:open ticker)
          vol (:vol ticker)
          symbol (:symbol ticker)
          last (:last ticker)
          buy (:buy ticker)
          sell (:sell ticker)
          high (:high ticker)
          low (:low ticker)]
      {:time time
       :open open
       :vol vol
       :last last})))

(defn get-kline
  "get kline 001 005 ..."
  [type]
  (let [url (case type
              "001" (str "http://api.huobi.com/staticmarket/btc_kline_" type "_json.js")
              "005" (str "http://api.huobi.com/staticmarket/btc_kline_" type "_json.js")
              "015" (str "http://api.huobi.com/staticmarket/btc_kline_" type "_json.js")
              "030" (str "http://api.huobi.com/staticmarket/btc_kline_" type "_json.js")
              "060" (str "http://api.huobi.com/staticmarket/btc_kline_" type "_json.js")
              "100" (str "http://api.huobi.com/staticmarket/btc_kline_" type "_json.js")
              (throw (Exception. "kline type error: " type)))]
    (json/read-str (:body (http-client/get url))
                   :key-fn keyword)))

(defn parse-kline-data
  "parse kline data from array to map"
  [data]
  (let [datetime (nth data 0)
        start-price (bigdec (nth data 1))
        top-price (bigdec (nth data 2))
        low-price (bigdec (nth data 3))
        end-price (bigdec (nth data 4))
        volume (bigdec (nth data 5))
        end-diff-price (- end-price start-price)
        max-diff-price (- top-price low-price)]
    {:datetime (str (.substring datetime 0 4)
                    "-"
                    (.substring datetime 4 6)
                    "-"
                    (.substring datetime 6 8)
                    " "
                    (.substring datetime 8 10)
                    ":"
                    (.substring datetime 10 12))
     :start-price start-price
     :top-price top-price
     :low-price low-price
     :mid-price (/ (+ top-price low-price) 2)
     :end-price end-price
     :volume volume
     :end-diff-price end-diff-price
     :end-diff-price-rate (with-precision 4 (/ end-diff-price start-price))
     :max-diff-price max-diff-price
     :max-diff-price-rate (with-precision 4 (/ max-diff-price low-price))
     :trend (cond
              (< end-diff-price 0) "down"
              (> end-diff-price 0) "up"
              (== end-diff-price 0) "flat"
              :else (throw (Exception. "end-diff-price error!")))}))

(defn get-account-info
  "get account info"
  [access_key secret_key]
  (let [unix-time (int (/ (System/currentTimeMillis) 1000))
        sign-str (str "access_key="
                      access_key
                      "&created="
                      unix-time
                      "&method=get_account_info&secret_key="
                      secret_key)
        sign (digest/md5 sign-str)]
    (json/read-str (:body (http-client/post "https://api.huobi.com/apiv3"
                                            {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                                             :form-params {:method "get_account_info"
                                                           :access_key access_key
                                                           :created unix-time
                                                           :sign sign}}))
                   :key-fn keyword)))

(defn buy
  "buy by amount & price"
  [access-key secret-key amount price]
  (let [unix-time (int (/ (System/currentTimeMillis) 1000))
        sign-str (str "access_key=" access-key
                      "&amount=" amount
                      "&coin_type=1"
                      "&created=" unix-time
                      "&method=buy"
                      "&price=" price
                      "&secret_key=" secret-key)
        sign (digest/md5 sign-str)]
    (json/read-str (:body (http-client/post "https://api.huobi.com/apiv3"
                                            {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                                             :form-params {:method "buy"
                                                           :access_key access-key
                                                           :coin_type 1
                                                           :price price
                                                           :amount amount
                                                           :created unix-time
                                                           :sign sign}}))
                   :key-fn keyword)))

(defn sell
  "sell by amount & price"
  [access-key secret-key amount price]
  (let [unix-time (int (/ (System/currentTimeMillis) 1000))
        sign-str (str "access_key=" access-key
                      "&amount=" amount
                      "&coin_type=1"
                      "&created=" unix-time
                      "&method=sell"
                      "&price=" price
                      "&secret_key=" secret-key)
        sign (digest/md5 sign-str)]
    (json/read-str (:body (http-client/post "https://api.huobi.com/apiv3"
                                            {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                                             :form-params {:method "sell"
                                                           :access_key access-key
                                                           :coin_type 1
                                                           :price price
                                                           :amount amount
                                                           :created unix-time
                                                           :sign sign}}))
                   :key-fn keyword)))

(defn buy-market
  "buy now"
  [access-key secret-key amount]
  (let [unix-time (int (/ (System/currentTimeMillis) 1000))
        sign-str (str "access_key=" access-key
                      "&amount=" amount
                      "&coin_type=1"
                      "&created=" unix-time
                      "&method=buy_market"
                      "&secret_key=" secret-key)
        sign (digest/md5 sign-str)]
    (json/read-str (:body (http-client/post "https://api.huobi.com/apiv3"
                                            {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                                             :form-params {:method "buy_market"
                                                           :access_key access-key
                                                           :coin_type 1
                                                           :amount amount
                                                           :created unix-time
                                                           :sign sign}}))
                   :key-fn keyword)))

(defn sell-market
  "sell now"
  [access-key secret-key amount]
  (let [unix-time (int (/ (System/currentTimeMillis) 1000))
        sign-str (str "access_key=" access-key
                      "&amount=" amount
                      "&coin_type=1"
                      "&created=" unix-time
                      "&method=sell_market"
                      "&secret_key=" secret-key)
        sign (digest/md5 sign-str)]
    (json/read-str (:body (http-client/post "https://api.huobi.com/apiv3"
                                            {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                                             :form-params {:method "sell_market"
                                                           :access_key access-key
                                                           :coin_type 1
                                                           :amount amount
                                                           :created unix-time
                                                           :sign sign}}))
                   :key-fn keyword)))

(defn loan-btc
  "loan now"
  [access-key secret-key amount]
  (let [unix-time (int (/ (System/currentTimeMillis) 1000))
        sign-str (str "access_key=" access-key
                      "&amount=" amount
                      "&created=" unix-time
                      "&loan_type=2"
                      "&method=loan"
                      "&secret_key=" secret-key)
        sign (digest/md5 sign-str)]
    (json/read-str (:body (http-client/post "https://api.huobi.com/apiv3"
                                            {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                                             :form-params {:method "loan"
                                                           :access_key access-key
                                                           :amount amount
                                                           :loan_type 2
                                                           :created unix-time
                                                           :sign sign}}))
                   :key-fn keyword)))

(defn repay-btc
  "repay btc"
  [access-key secret-key loan-id amount]
  (let [unix-time (int (/ (System/currentTimeMillis) 1000))
        sign-str (str "access_key=" access-key
                      "&amount=" amount
                      "&created=" unix-time
                      "&loan_id=" loan-id
                      "&method=repayment"
                      "&secret_key=" secret-key)
        sign (digest/md5 sign-str)]
    (json/read-str (:body (http-client/post "https://api.huobi.com/apiv3"
                                            {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                                             :form-params {:method "repayment"
                                                           :access_key access-key
                                                           :loan_id loan-id
                                                           :amount amount
                                                           :created unix-time
                                                           :sign sign}}))
                   :key-fn keyword)))

(defn write-a-object
  "write a object append to a file"
  [a-object a-file]
  (spit a-file (str (json/write-str a-object) "\n") :append true))

(defn read-a-json-file
  "read a json file by filter"
  ([a-file]
   (with-open [rdr (clojure.java.io/reader a-file)]
     (map #(json/read-str %
                          :key-fn keyword)
          (doall (line-seq rdr)))))
  ([a-file filter-fn]
   (with-open [rdr (clojure.java.io/reader a-file)]
     (filter filter-fn
             (map #(json/read-str %
                                  :key-fn keyword)
                  (doall (line-seq rdr)))))))
