(ns rocks.pho.btc-robot-web.utils
  (:require [clj-http.client :as http-client]
            [clojure.data.json :as json]
            [clj-time.coerce :as coerce-time]
            [clj-time.format :as format-time]
            [digest :as digest]))

(defn get-readable-time
  "get now yyyy-MM-dd HH:mm:ss"
  [long-time]
  (let [t (coerce-time/from-long (* (condp = (type long-time)
                                      String (Long/parseLong long-time)
                                      Long long-time
                                      Integer long-time
                                      (throw (Exception. (str long-time " type: " (type long-time) " NOT Long or String!"))))
                                    (case (.length (str long-time))
                                      10 1000
                                      13 1
                                      (throw (Exception. (str long-time " length error!"))))))
        f (format-time/formatter-local "yyyy-MM-dd HH:mm:ss")]
    (format-time/unparse f t)))

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
  "load now"
  [access-key secret-key amount]
  (let [unix-time (int (/ (System/currentTimeMillis) 1000))
        sign-str (str "access_key=" access-key
                      "&amount=" amount
                      "&created=" unix-time
                      "&loan_type=2"
                      "&method=load"
                      "&secret_key=" secret-key)
        sign (digest/md5 sign-str)]
    (json/read-str (:body (http-client/post "https://api.huobi.com/apiv3"
                                            {:headers {"Content-Type" "application/x-www-form-urlencoded"}
                                             :form-params {:method "loan"
                                                           :access_key access-key
                                                           :amount amount
                                                           :loan_type 2
                                                           :created unix-time
                                                           :sign sign}})))))

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
                                                           :sign sign}})))))

(defn write-a-map
  "write a map append to a file"
  [a-map a-file]
  (spit a-file (str (json/write-str a-map) "\n") :append true))

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
