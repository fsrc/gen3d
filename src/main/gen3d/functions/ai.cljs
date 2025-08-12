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
    [(gk/google-ai {:apiKey google-api-key})
     (gk/vertex-ai {:location "europe-west1"})
     (gk/open-ai {:apiKey openai-api-key})]}))

(def openai-client (openai/client openai-api-key))

(def generate-poem-flow
  (gk/define-flow
    ai
    {:name "generate-poem"
     :inputSchema (z/object
                   {:subject (z/string)})
     :outputSchema (z/string)}

    (fn [^js game-data]
      (let [subject (.-subject game-data)
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
     :inputSchema (z/array (z/string))
     :outputSchema (z/string)}

    (fn [^js input-data]
      (let [chat (s/join "\n" input-data)
            prompt
            (str
             "Generate 3D object vertices and indices for object described in chat.\n\n"
             "Requirements:\n"
             "- Return vertices as array of numbers: [x1,y1,z1,r1,g1,b1, x2,y2,z2,r2,g2,b2, ...]\n"
             "- Each vertex has 6 values: position (x,y,z) + color (r,g,b) where colors are 0.0-1.0\n"
             "- Return indices as array of integers for triangles: [0,1,2, 0,2,3, ...]\n"
             "- Object should be centered at origin and fit within -2 to +2 coordinate range\n"
             "- Use appropriate colors for the object based on the color scheme\n\n"
             "Respond with valid JSON with NO comments in this exact format:\n"
             "{\n"
             "  \"vertices\": [x1,y1,z1,r1,g1,b1, x2,y2,z2,r2,g2,b2, ...],\n"
             "  \"indices\": [0,1,2, 0,2,3, ...],\n"
             "  \"description\": \"Brief description of the generated object\"\n"
             "}\n\n"
             "Examples of objects to generate based on complexity:\n"
             "- Simple: cube, pyramid, sphere (low-poly)\n"
             "- Medium: house, tree, car (moderate detail)\n"
             "- Complex: character, detailed building, organic shapes\n"
             "}\n\n"
             "Example of output:\n"
             "{\n"
             "  \"vertices\": []\n"
             "               -1.0,-1.0,1.0,1.0,0.0,0.0,\n"
             "               1.0,-1.0,1.0,1.0,0.0,0.0,\n"
             "               1.0,1.0,1.0,1.0,0.0,0.0,\n"
             "               -1.0,1.0,1.0,1.0,0.0,0.0,\n"
             "               -1.0,-1.0,-1.0,0.0,1.0,0.0,\n"
             "               -1.0,1.0,-1.0,0.0,1.0,0.0,\n"
             "               1.0,1.0,-1.0,0.0,1.0,0.0,\n"
             "               1.0,-1.0,-1.0,0.0,1.0,0.0,\n"
             "               -1.0,1.0,-1.0,0.0,0.0,1.0,\n"
             "               -1.0,1.0,1.0,0.0,0.0,1.0,\n"
             "               1.0,1.0,1.0,0.0,0.0,1.0,\n"
             "               1.0,1.0,-1.0,0.0,0.0,1.0,\n"
             "               -1.0,-1.0,-1.0,1.0,1.0,0.0,\n"
             "               1.0,-1.0,-1.0,1.0,1.0,0.0,\n"
             "               1.0,-1.0,1.0,1.0,1.0,0.0,\n"
             "               -1.0,-1.0,1.0,1.0,1.0,0.0,\n"
             "               1.0,-1.0,-1.0,1.0,0.0,1.0,\n"
             "               1.0,1.0,-1.0,1.0,0.0,1.0,\n"
             "               1.0,1.0,1.0,1.0,0.0,1.0,\n"
             "               1.0,-1.0,1.0,1.0,0.0,1.0,\n"
             "               -1.0,-1.0,-1.0,0.0,1.0,1.0,\n"
             "               -1.0,-1.0,1.0,0.0,1.0,1.0,\n"
             "               -1.0,1.0,1.0,0.0,1.0,1.0,\n"
             "               -1.0,1.0,-1.0,0.0,1.0,1.0\n"
             "  ,\n"
             "  \"indices\": []\n"
             "              0,1,2,0,2,3,\n"
             "              4,5,6,4,6,7,\n"
             "              8,9,10,8,10,11,\n"
             "              12,13,14,12,14,15,\n"
             "              16,17,18,16,18,19,\n"
             "              20,21,22,20,22,23\n"
             "  ,\n"
             "  \"description\": \"A colorful cube\"\n"
             "}\n"
             "\n"
             "Chat:\n"
             chat)]

            
        (js/console.log prompt)
        (->
         (gk/generate ai #js {:model 
                              ; (gk/google-ai-model "gemini-2.5-flash")
                              ; gk/openai-gpt4o-model
                              "openai/gpt-5"
                              :prompt prompt})
         (p/then (fn [result]
                   (let 
                     [response-text (.-text result)
                      first-step (s/replace response-text #"```.*" "")]
                     first-step))))))))

(def exports
  #js {:generatePoem generate-poem-flow
       :generate3DObject generate-3d-object-flow})

