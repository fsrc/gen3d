(ns gen3d.viewer.router
  (:require [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [reitit.coercion.spec :as rss]
            [gen3d.viewer.state :refer [state sw-current!]]
            [gen3d.viewer.views.home :as home]
            [gen3d.viewer.views.login :as login]))

(def routes
  [
   ["/" 
    {:name   :home
     :secure true
     :view   [home/show]}]
   ["/login" 
    {:name   :login
     :secure true
     :view   [login/show]}]])



(defn on-navigate [match history]
  (sw-current! assoc :route match))

(defn init []
  (rfe/start!
    (rf/router routes {:data {:coercion rss/coercion}})
    on-navigate
    {:use-fragment true}))

(defn route-allowed [route authenticated-fn]
  (if
    (get-in route [:data :secure])
    (authenticated-fn)
    true))

