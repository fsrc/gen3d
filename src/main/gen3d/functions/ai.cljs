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

    (fn [^js game-data]
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



(def generate-3d-object-flow
  (gk/define-flow
    ai
    {:name "generate-3d-object"
     :inputSchema (z/string)
     :outputSchema (z/string)}

    (fn [^js input-data]
      (let [
            prompt
            (str
              "You are a 3D model generator.\n"
              "Your job is to take any description from a non-technical user and generate a valid JSON object describing the 4D mesh, following the exact schema below.\n"
              "You do all geometry generation yourself — the user will not provide coordinates or indices.\n"
              "\n"
              "Output Rules\n"
              "\n"
              "Always output only a JSON object, no extra text, markdown, or explanations.\n"
              "\n"
              "Follow this schema exactly:\n"
              "\n"
              "{\n"
              "  \"geometry\": {\n"
              "    \"type\": \"CustomGeometry\",\n"
              "    \"vertices\": [[x,y,z], ...],\n"
              "    \"indices\": [i0, i1, i2, ...]\n"
              "  },\n"
              "  \"material\": {\n"
              "    \"type\": \"MeshPhongMaterial\",\n"
              "    \"parameters\": {\n"
              "      \"color\": <int>,\n"
              "      \"shininess\": <number>\n"
              "    }\n"
              "  },\n"
              "  \"position\": {\"x\": <number>, \"y\": <number>, \"z\": <number>},\n"
              "  \"rotation\": {\"x\": <number>, \"y\": <number>, \"z\": <number>},\n"
              "  \"scale\": {\"x\": <number>, \"y\": <number>, \"z\": <number>}\n"
              "}\n"
              "\n"
              "\n"
              "Vertices: 3D coordinates in a right-handed coordinate system (+X right, +Y up, +Z toward viewer).\n"
              "\n"
              "Indices: integers in groups of 3, counter-clockwise winding for outward faces.\n"
              "\n"
              "Units: Default to unit scale unless otherwise implied by the description.\n"
              "\n"
              "Material defaults:\n"
              "\n"
              "If no color is mentioned, choose a neutral color (0x777777).\n"
              "\n"
              "If no shininess is mentioned, use 100.\n"
              "\n"
              "If the description is vague, choose the most common/simple representation of that object.\n"
              "\n"
              "If the object cannot be represented in this format, output:\n"
              "\n"
              "{\"error\":\"CANNOT_GENERATE\",\"reason\":\"<short reason>\"}\n"
              "\n"
              "User request:\n"
              "\n"
              input-data "\n\n")]

            
        (js/console.log prompt)
        (->
         (gk/generate ai #js {:model 
                              (gk/google-ai-model "gemini-2.5-flash")
                              ; gk/openai-gpt4o-model
                              ; "openai/gpt-5"
                              :prompt prompt})
         (p/then (fn [result]
                   (let 
                     [response-text (.-text result)
                      first-step (s/replace response-text #"```.*" "")]
                     (js/console.log "Response text: " first-step)
                     first-step))))))))


(def exports
  #js {
       :generatePoem generate-poem-flow
       :generate3DObjectFlow generate-3d-object-flow})

