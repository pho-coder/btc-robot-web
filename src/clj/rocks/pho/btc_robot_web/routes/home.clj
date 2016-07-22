(ns rocks.pho.btc-robot-web.routes.home
  (:require [rocks.pho.btc-robot-web.layout :as layout]
            [compojure.core :refer [defroutes GET]]
            [ring.util.http-response :as response]
            [clojure.java.io :as io]
            [rocks.pho.btc-robot-web.utils :as utils]
            [rocks.pho.btc-robot-web.events :as events]))

(defn home-page []
  (layout/render
   "home.html" {:docs (-> "docs/docs.md" io/resource slurp)
                :staticmarket (utils/get-staticmarket)
                :my-wallet events/my-wallet}))

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

(defroutes home-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page))
  (GET "/buy" [code amount price] (buy code amount price))
  (GET "/sell" [code amount price] (sell code amount price))
  (GET "/buy-all" [code] (buy-all code))
  (GET "/sell-all" [code] (sell-all code))
  (GET "/loan-all" [code] (loan-all code))
  (GET "/repay-all" [code] (repay-all code)))
