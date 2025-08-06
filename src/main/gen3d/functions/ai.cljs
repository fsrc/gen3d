(ns gen3d.functions.ai
  (:require
   [promesa.core :as p]
   [cljs.reader :as reader]
   [clojure.string :as s]
   [anr.fire.environment :as fe]
   [anr.fire.genkit :as gk]
   [anr.fire.zod :as z]
   [anr.fire.openai :as openai]
   ["stream/web" :as web-stream]
   ["fs" :as fs]
   ["firebase-functions/params" :as params]))


(defn read-edn [path]
  (-> (.readFileSync fs path "utf8")
      reader/read-string))

(def secrets
  (when fe/EMULATOR
    (read-edn "./secrets.edn")))

(def google-api-key
  (if (nil? secrets)
    (.value (params/defineSecret "GOOGLE_API_KEY"))
    (:GOOGLE-API-KEY secrets)))

(def openai-api-key
  (if (nil? secrets)
    (.value (params/defineSecret "OPENAI_API_KEY"))
    (:OPENAI-API-KEY secrets)))

(def ai 
  (gk/genkit 
    {:plugins 
     [
      (gk/google-ai {:apiKey google-api-key}) 
      (gk/vertex-ai {:location "europe-west1"})
      (gk/open-ai {:apiKey openai-api-key})]})) 

(def openai-client (openai/client openai-api-key))


(def generate-poem-flow
  (gk/define-flow
    ai
    {:name "generate-poem"
     :inputSchema (z/object 
                    {
                     :subject (z/string)})
     :outputSchema (z/string)}

    (fn [game-data]
      (let [
            subject (.-subject game-data)
            prompt 
            (str 
              "Write a poem about '" subject "'. ")]
        (->
         (gk/generate ai #js {:model (gk/google-ai-model "gemini-2.5-flash")
                              :prompt prompt})
         (p/then (fn [result]
                   (let [poem (.-text result)]
                     (if (empty? poem)
                       "# No poem Generated\n\nPlease try again."
                       poem)))))))))


(def exports
  #js {
       :generatePoem generate-poem-flow})

