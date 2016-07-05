(ns rocks.pho.btc-robot-web.utils
  (:require [clj-http.client :as http-client]
            [clojure.data.json :as json]
            [clj-time.coerce :as coerce-time]
            [clj-time.format :as format-time]
            [digest :as digest]))

(defn get-readable-time
  "get now yyyy-MM-dd HH:mm:ss"
  [long-time]
  (let [t (coerce-time/from-long (* (Integer/parseInt long-time) 1000))
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
