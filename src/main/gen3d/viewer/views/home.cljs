(ns gen3d.viewer.views.home
  (:require
    [reagent.core :as r]
    [reitit.frontend.easy :as rfe]
    [promesa.core :as p]
    [clojure.string :as s]

    [anr.fire.web :as fire]

    [gen3d.viewer.state :refer [state]]))


(defn show 
  []
  [:div "Home"])
