(ns rocks.pho.btc-robot-web.handler
  (:require [compojure.core :refer [routes wrap-routes]]
            [rocks.pho.btc-robot-web.layout :refer [error-page]]
            [rocks.pho.btc-robot-web.routes.home :refer [home-routes]]
            [compojure.route :as route]
            [rocks.pho.btc-robot-web.env :refer [defaults]]
            [mount.core :as mount]
            [rocks.pho.btc-robot-web.middleware :as middleware]))

(mount/defstate init-app
                :start ((or (:init defaults) identity))
                :stop  ((or (:stop defaults) identity)))

(def app-routes
  (routes
    (-> #'home-routes
        (wrap-routes middleware/wrap-csrf)
        (wrap-routes middleware/wrap-formats))
    (route/not-found
      (:body
        (error-page {:status 404
                     :title "page not found"})))))


(defn app [] (middleware/wrap-base #'app-routes))
