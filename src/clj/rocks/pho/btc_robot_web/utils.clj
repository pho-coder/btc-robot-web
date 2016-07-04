(ns rocks.pho.btc-robot-web.utils
  (:require [clj-http.client :as http-client]
            [clojure.data.json :as json]
            [clj-time.coerce :as coerce-time]
            [clj-time.format :as format-time]))

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
