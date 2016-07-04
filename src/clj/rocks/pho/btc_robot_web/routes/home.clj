(ns rocks.pho.btc-robot-web.routes.home
  (:require [rocks.pho.btc-robot-web.layout :as layout]
            [compojure.core :refer [defroutes GET]]
            [ring.util.http-response :as response]
            [clojure.java.io :as io]
            [rocks.pho.btc-robot-web.utils :as utils]))

(defn home-page []
  (layout/render
   "home.html" {:docs (-> "docs/docs.md" io/resource slurp)
                :staticmarket (utils/get-staticmarket)}))

(defn about-page []
  (layout/render "about.html"))

(defroutes home-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page)))

