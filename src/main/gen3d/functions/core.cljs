(ns gen3d.functions.core
  (:require 
    [anr.fire.admin :as fire]
    [anr.fire.functions-v2 :as f]
    [anr.fire.genkit :as gk]
    [gen3d.functions.login :as login]
    [gen3d.functions.ai :as ai]))



(defonce _ (fire/init))

(f/set-global-options 
  {:maxInstances 10
   :timeoutSeconds 540})

(def gemeni-api-key (f/define-secret "GEMINI_API_KEY"))
      

(def exports 
  #js {
       :onCreateUser login/on-create-user


       :generatePoem
       (f/on-call-genkit 
         {:secrets [gemeni-api-key]}
          ; :authPolicy (f/has-claim "email_verified")
          ; :enforceAppCheck true}

         ai/generate-poem-flow)})

