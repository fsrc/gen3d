(ns gen3d.viewer.core
  (:require 
    [reagent.dom.client :as r]
    [anr.fire.web :as fire]
    [cljs.core :as c]
    [gen3d.viewer.router :as router]
    [gen3d.viewer.state :refer [state sw-current!]]
    [gen3d.viewer.views.login :as login]))

(defonce root (atom nil))

(def firebase-config-prod 
  {
    :apiKey "AIzaSyACNiNvIET1HflQxLHj44iDznGnkuH0BVI",
    :authDomain "gen3d-cljs.firebaseapp.com",
    :projectId "gen3d-cljs",
    :storageBucket "gen3d-cljs.firebasestorage.app",
    :messagingSenderId "1042590468355",
    :appId "1:1042590468355:web:f9e93f9fac6d3fb6ed4136"})

(def firebase-config firebase-config-prod)

(defn faulty-route []
  [:div.bg-gray-50.overflow-hidden.rounded-lg
   [:div.px-4.py-5.sm:p-6 "404"]])

(defn show-loader []
  [:div.min-h-screen.bg-gray-50.flex.flex-col.justify-center.py-12.sm:px-6.lg:px-8
   [:div.flex.justify-center.items-center
    [:div.animate-spin.rounded-full.h-32.w-32.border-b-2.border-gray-900]]])

(defn logged-in? []
  (not (nil? (:user @state))))

;; Root component
(defn app []
  (let [route (:route @state)]
    ; (js/console.log (get-in route [:data :name]))
    (cond 
      ;; Booting
      (:booting @state)
      [show-loader]

      ;; 404
      (nil? route)
      [faulty-route]

      ;; Secure
      (router/route-allowed route logged-in?)
      ; [:<> ^{:key (gensym (get-in route [:data :name]))}
      (get-in route [:data :view])

      ;; Login
      :else
      [login/show])))


(defn stop []
  (js/console.log "Stopping...")
  (r/unmount @root))

(defn start []
  (js/console.log "Initializing...")
  (router/init)
  (reset! root
          (r/create-root
            (.getElementById js/document "app")))

  (r/render @root [app]))

(defn auth-changed [auth]
  (case (:state auth)
    :authenticated
    (sw-current! assoc :user (:id auth) :booting false)
    :unauthenticated
    (sw-current! assoc :user nil :booting false)
    :starting
    (sw-current! assoc :user nil :booting true))


  (js/console.log "Auth changed"))

(defn ^:export init []
  (js/console.log "Cold start...")

  ;; Initialize firebase
  (fire/init firebase-config auth-changed)
  (start))


(comment
  (fire/sign-out))

