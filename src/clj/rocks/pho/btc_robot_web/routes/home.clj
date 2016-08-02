(ns rocks.pho.btc-robot-web.routes.home
  (:require [clojure.tools.logging :as log]
            [mount.core :as mount]
            [rocks.pho.btc-robot-web.layout :as layout]
            [compojure.core :refer [defroutes GET POST]]
            [ring.util.http-response :as response]
            [clojure.java.io :as io]
            [rocks.pho.btc-robot-web.utils :as utils]
            [rocks.pho.btc-robot-web.events :as events]
            [rocks.pho.btc-robot-web.watcher :as watcher]))

(defn home-page []
  (layout/render
   "home.html" {:docs (-> "docs/docs.md" io/resource slurp)
                :staticmarket (utils/get-staticmarket)
                :my-wallet events/my-wallet
                :price-diff (- (:last (utils/get-staticmarket))
                               watcher/start-price)
                :net-asset-diff (- watcher/net-asset
                                   watcher/start-net-asset)
                :events (utils/read-a-json-file events/events-log-file)
                :buy-point-down-times-least (:down-times-least watcher/buy-point)
                :buy-point-down-price-least (:down-price-least watcher/buy-point)
                :buy-point-up-times-least (:up-times-least watcher/buy-point)
                :buy-point-up-price-least (:up-price-least watcher/buy-point)
                :sell-point-down-times-least (:down-times-least watcher/sell-point)
                :sell-point-down-price-least (:down-price-least watcher/sell-point)}))

(defn about-page []
  (layout/render "about.html"))

(defn buy [code amount price]
  (layout/render
   "buy.html" (if (= code "7")
                (let [re (events/one-deal "buy"
                                          (Float/parseFloat amount)
                                          (Float/parseFloat price))]
                  {:success (:success re)
                   :info (:info re)})
                {:success "error"})))

(defn sell [code amount price]
  (layout/render
   "sell.html" (if (= code "7")
                 (let [re (events/one-deal "sell"
                                           (Float/parseFloat amount)
                                           (Float/parseFloat price))]
                   {:success (:success re)
                    :info (:info re)})
                 {:success "error"})))

(defn buy-all [code]
  (layout/render
   "buy.html" (if (= code "7")
                (let [re (events/show-hand "buy")]
                  {:success (:success re)
                   :info (:info re)})
                {:success "error"})))

(defn sell-all [code]
  (layout/render
   "sell.html" (if (= code "7")
                 (let [re (events/show-hand "sell")]
                   {:success (:success re)
                    :info (:info re)})
                 {:success "error"})))

(defn loan-all [code]
  (layout/render
   "loan.html" (if (= code "7")
                 (let [re (events/lever-btc "loan" 1)]
                   {:success (:success re)
                    :info (:info re)})
                 {:success "error"})))

(defn repay-all [code]
  (layout/render
   "repay.html" (if (= code "7")
                  (let [re (events/lever-btc "repay" 1)]
                    {:success (:success re)
                     :info (:info re)})
                  {:success "error"})))

(defn stop []
  (layout/render
   "about.html" (System/exit 0)))

(defn modify-buy-point [down-times-least down-price-least up-times-least up-price-least]
  (mount/start-with {#'watcher/buy-point {:down-times-least (bigdec down-times-least)
                                          :down-price-least (bigdec down-price-least)
                                          :up-times-least (bigdec up-times-least)
                                          :up-price-least (bigdec up-price-least)}})
  (home-page))

(defn modify-sell-point [down-times-least down-price-least]
  (mount/start-with {#'watcher/sell-point {:down-times-least down-times-least
                                           :down-price-least down-price-least}})
  (home-page))

(defn reset-all []
  (mount/start-with {#'watcher/reset-all true})
  (response/found "/"))

(defroutes home-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page))
  (GET "/buy" [code amount price] (buy code amount price))
  (GET "/sell" [code amount price] (sell code amount price))
  (GET "/buy-all" [code] (buy-all code))
  (GET "/sell-all" [code] (sell-all code))
  (GET "/loan-all" [code] (loan-all code))
  (GET "/repay-all" [code] (repay-all code))
  (GET "/stop" [] (stop))
  (POST "/buy-point" [down-times-least down-price-least up-times-least up-price-least] (modify-buy-point down-times-least down-price-least up-times-least up-price-least))
  (POST "/sell-point" [down-times-least down-price-least] (modify-sell-point down-times-least down-price-least))
  (GET "/reset-all" [] (reset-all)))
