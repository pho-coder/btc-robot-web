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

(defn buy-all [code]
  (layout/render
   "buy.html" (if (= code "7")
                {:success (:success (events/buy-all))}
                {:success "error"})))

(defroutes home-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page))
  (GET "/buy-all" [code] (buy-all code)))

