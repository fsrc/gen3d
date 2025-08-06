(ns gen3d.viewer.state
  (:require 
    [reagent.core :as r]

    [anr.fire.web :as fire]
    [anr.fire.utils :refer [col-snap]]

    [anr.effects.core :refer [defrule initialize-effects]]))


(defonce state
  (r/atom
    {:user nil
     :booting true
     :route nil}))

(def sw-current! (initialize-effects state))



