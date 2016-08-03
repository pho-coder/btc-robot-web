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
                :down-up-point-down-times-least (:down-times-least watcher/down-up-point)
                :down-up-point-down-price-least (:down-price-least watcher/down-up-point)
                :down-up-point-up-times-least (:up-times-least watcher/down-up-point)
                :down-up-point-up-price-least (:up-price-least watcher/down-up-point)
                :up-point-up-times-least (:up-times-least watcher/up-point)
                :up-point-up-price-least (:up-price-least watcher/up-point)
                :down-point-down-times-least (:down-times-least watcher/down-point)
                :down-point-down-price-least (:down-price-least watcher/down-point)}))

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

(defn modify-down-up-point [down-times-least down-price-least up-times-least up-price-least]
  (mount/start-with {#'watcher/down-up-point {:down-times-least (bigdec down-times-least)
                                              :down-price-least (bigdec down-price-least)
                                              :up-times-least (bigdec up-times-least)
                                              :up-price-least (bigdec up-price-least)}})
  (response/found "/"))

(defn modify-up-point [up-times-least up-price-least]
  (mount/start-with {#'watcher/up-point {:up-times-least (bigdec up-times-least)
                                         :up-price-least (bigdec up-price-least)}}))

(defn modify-down-point [down-times-least down-price-least]
  (mount/start-with {#'watcher/down-point {:down-times-least (bigdec down-times-least)
                                           :down-price-least (bigdec down-price-least)}})
  (response/found "/"))

(defn reset-all []
  (mount/start-with {#'watcher/reset-all (true? true)})
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
  (POST "/down-up-point" [down-times-least down-price-least up-times-least up-price-least] (modify-down-up-point down-times-least down-price-least up-times-least up-price-least))
  (POST "up-point" [up-times-least up-price-least] (modify-up-point up-times-least up-price-least))
  (POST "/down-point" [down-times-least down-price-least] (modify-down-point down-times-least down-price-least))
  (GET "/reset-all" [] (reset-all)))
