(ns gen3d.viewer.views.home
  (:require
   [reagent.core :as r]
   [reitit.frontend.easy :as rfe]
   [promesa.core :as p]
   [clojure.string :as s]

   [anr.fire.web :as fire]

   [gen3d.viewer.state :refer [state]]
   ["./torus.js" :as torus]))

(defonce render-mode (atom :solid)) ; :solid, :wireframe, or :points

(defonce generated-object (atom nil)) ; Stores generated vertices and indices

; (js/console.log torus)
(defn keyboard-events
  [e]
  (case (.-key e)
    "1" (reset! render-mode :solid)
    "2" (reset! render-mode :wireframe)
    "3" (reset! render-mode :points)
    nil))

(.addEventListener js/document "keydown" keyboard-events)

(defn create-shader [gl type source]
  (let [shader (.createShader gl type)]
    (.shaderSource gl shader source)
    (.compileShader gl shader)
    (when-not (.getShaderParameter gl shader (.-COMPILE_STATUS gl))
      (js/console.error "Shader compilation error:" (.getShaderInfoLog gl shader))
      (.deleteShader gl shader)
      nil)
    shader))

(defn create-program [gl vertex-shader fragment-shader]
  (let [program (.createProgram gl)]
    (.attachShader gl program vertex-shader)
    (.attachShader gl program fragment-shader)
    (.linkProgram gl program)
    (when-not (.getProgramParameter gl program (.-LINK_STATUS gl))
      (js/console.error "Program linking error:" (.getProgramInfoLog gl program))
      (.deleteProgram gl program)
      nil)
    program))

(defn create-cube-vertices []
  (js/Float32Array.
   (clj->js
    [-1.0 -1.0 1.0 1.0 0.0 0.0
     1.0 -1.0 1.0 1.0 0.0 0.0
     1.0 1.0 1.0 1.0 0.0 0.0
     -1.0 1.0 1.0 1.0 0.0 0.0

     -1.0 -1.0 -1.0 0.0 1.0 0.0
     -1.0 1.0 -1.0 0.0 1.0 0.0
     1.0 1.0 -1.0 0.0 1.0 0.0
     1.0 -1.0 -1.0 0.0 1.0 0.0

     -1.0 1.0 -1.0 0.0 0.0 1.0
     -1.0 1.0 1.0 0.0 0.0 1.0
     1.0 1.0 1.0 0.0 0.0 1.0
     1.0 1.0 -1.0 0.0 0.0 1.0

     -1.0 -1.0 -1.0 1.0 1.0 0.0
     1.0 -1.0 -1.0 1.0 1.0 0.0
     1.0 -1.0 1.0 1.0 1.0 0.0
     -1.0 -1.0 1.0 1.0 1.0 0.0

     1.0 -1.0 -1.0 1.0 0.0 1.0
     1.0 1.0 -1.0 1.0 0.0 1.0
     1.0 1.0 1.0 1.0 0.0 1.0
     1.0 -1.0 1.0 1.0 0.0 1.0

     -1.0 -1.0 -1.0 0.0 1.0 1.0
     -1.0 -1.0 1.0 0.0 1.0 1.0
     -1.0 1.0 1.0 0.0 1.0 1.0
     -1.0 1.0 -1.0 0.0 1.0 1.0])))

(defn create-cube-indices []
  (js/Uint16Array.
   (clj->js
    [0 1 2 0 2 3
     4 5 6 4 6 7
     8 9 10 8 10 11
     12 13 14 12 14 15
     16 17 18 16 18 19
     20 21 22 20 22 23])))

(defn object-vertices []
  (if-let [^js generated @generated-object]
    (js/Float32Array. (.-vertices generated))
    (js/Float32Array. (.-vertices torus))))

(defn object-indices []
  (if-let [generated @generated-object]
    (js/Uint16Array. (.-indices generated))
    (js/Uint16Array. (.-indices torus))))

(defn create-wireframe-indices []
  "Creates indices for wireframe edges - lines connecting cube corners"
  (js/Uint16Array.
   (clj->js
    [;; Front face edges (vertices 0-3)
     0 1, 1 2, 2 3, 3 0
     ;; Back face edges (vertices 4-7)
     4 5, 5 6, 6 7, 7 4
     ;; Top face edges (vertices 8-11)
     8 9, 9 10, 10 11, 11 8
     ;; Bottom face edges (vertices 12-15)
     12 13, 13 14, 14 15, 15 12
     ;; Right face edges (vertices 16-19)
     16 17, 17 18, 18 19, 19 16
     ;; Left face edges (vertices 20-23)
     20 21, 21 22, 22 23, 23 20])))

(defn multiply-matrices [a b]
  (let [result (js/Array. 16)]
    (dotimes [i 4]
      (dotimes [j 4]
        (aset result (+ (* i 4) j)
              (+ (* (aget a (+ (* i 4) 0)) (aget b (+ (* 0 4) j)))
                 (* (aget a (+ (* i 4) 1)) (aget b (+ (* 1 4) j)))
                 (* (aget a (+ (* i 4) 2)) (aget b (+ (* 2 4) j)))
                 (* (aget a (+ (* i 4) 3)) (aget b (+ (* 3 4) j)))))))
    (js/Float32Array. result)))

(defn create-rotation-matrix [angle-x angle-y angle-z]
  (let [cos-x (js/Math.cos angle-x)
        sin-x (js/Math.sin angle-x)
        cos-y (js/Math.cos angle-y)
        sin-y (js/Math.sin angle-y)
        cos-z (js/Math.cos angle-z)
        sin-z (js/Math.sin angle-z)

        rot-x (js/Float32Array. #js [1 0 0 0
                                     0 cos-x (- sin-x) 0
                                     0 sin-x cos-x 0
                                     0 0 0 1])

        rot-y (js/Float32Array. #js [cos-y 0 sin-y 0
                                     0 1 0 0
                                     (- sin-y) 0 cos-y 0
                                     0 0 0 1])

        rot-z (js/Float32Array. #js [cos-z (- sin-z) 0 0
                                     sin-y cos-y 0 0
                                     0 0 1 0
                                     0 0 0 1])]

    (-> rot-x
        (multiply-matrices rot-y)
        (multiply-matrices rot-z))))

(defn create-single-axis-rotation [angle]
  (let [cos-a (js/Math.cos angle)
        sin-a (js/Math.sin angle)
        axis-x (/ 1 (js/Math.sqrt 3))
        axis-y (/ 1 (js/Math.sqrt 3))
        axis-z (/ 1 (js/Math.sqrt 3))

        one-minus-cos (- 1 cos-a)]
    (js/Float32Array.
     #js [(+ cos-a (* one-minus-cos axis-x axis-x))
          (- (* one-minus-cos axis-x axis-y) (* sin-a axis-z))
          (+ (* one-minus-cos axis-x axis-z) (* sin-a axis-y))
          0

          (+ (* one-minus-cos axis-y axis-x) (* sin-a axis-z))
          (+ cos-a (* one-minus-cos axis-y axis-y))
          (- (* one-minus-cos axis-y axis-z) (* sin-a axis-x))
          0

          (- (* one-minus-cos axis-z axis-x) (* sin-a axis-y))
          (+ (* one-minus-cos axis-z axis-y) (* sin-a axis-x))
          (+ cos-a (* one-minus-cos axis-z axis-z))
          0

          0 0 0 1])))

(defn create-perspective-matrix [fov aspect near far]
  (let [f (/ 1.0 (js/Math.tan (/ fov 2)))
        range-inv (/ 1.0 (- near far))]
    (js/Float32Array.
     #js [(/ f aspect) 0 0 0
          0 f 0 0
          0 0 (* (+ far near) range-inv) -1
          0 0 (* (* far near 2) range-inv) 0])))

(defn webgl-cube []
  (let [canvas-ref (atom nil)
        animation-id (atom nil)
        key-handler (atom nil)
        resize-handler (atom nil)]

    (r/create-class
     {:component-did-mount
      (fn [this]

        (let [canvas @canvas-ref
              gl (.getContext canvas "webgl")

              ;; Resize handler to update canvas and viewport
              handle-resize (fn []
                              (let [width (.-innerWidth js/window)
                                    height (.-innerHeight js/window)]
                                (set! (.-width canvas) width)
                                (set! (.-height canvas) height)
                                (when gl
                                  (.viewport gl 0 0 width height))))]

          ;; Add resize event listener
          (reset! resize-handler handle-resize)
          (.addEventListener js/window "resize" handle-resize)

          (when gl
            (let [vertex-shader-source
                  "attribute vec3 aVertexPosition;
                   attribute vec3 aVertexColor;
                   uniform mat4 uModelMatrix;
                   uniform mat4 uViewMatrix;
                   uniform mat4 uProjectionMatrix;
                   varying vec3 vColor;
                   
                   void main(void) {
                     vec4 worldPosition = uModelMatrix * vec4(aVertexPosition, 1.0);
                     gl_Position = uProjectionMatrix * uViewMatrix * worldPosition;
                     gl_PointSize = 8.0;
                     vColor = aVertexColor;
                   }"

                  fragment-shader-source
                  "precision mediump float;
                   varying vec3 vColor;
                   
                   void main(void) {
                     gl_FragColor = vec4(vColor, 1.0);
                   }"

                  vertex-shader (create-shader gl (.-VERTEX_SHADER gl) vertex-shader-source)
                  fragment-shader (create-shader gl (.-FRAGMENT_SHADER gl) fragment-shader-source)
                  program (create-program gl vertex-shader fragment-shader)]

              (when program
                (.useProgram gl program)

                (let [position-location (.getAttribLocation gl program "aVertexPosition")
                      color-location (.getAttribLocation gl program "aVertexColor")
                      model-location (.getUniformLocation gl program "uModelMatrix")
                      view-location (.getUniformLocation gl program "uViewMatrix")
                      projection-location (.getUniformLocation gl program "uProjectionMatrix")

                      vertex-buffer (.createBuffer gl)
                      index-buffer (.createBuffer gl)
                      wireframe-buffer (.createBuffer gl)

                      vertices (object-vertices)
                      indices (object-indices)
                      wireframe-indices (create-wireframe-indices)]

                  ;; Setup vertex buffer
                  (.bindBuffer gl (.-ARRAY_BUFFER gl) vertex-buffer)
                  (.bufferData gl (.-ARRAY_BUFFER gl) vertices (.-STATIC_DRAW gl))

                  ;; Setup triangle index buffer
                  (.bindBuffer gl (.-ELEMENT_ARRAY_BUFFER gl) index-buffer)
                  (.bufferData gl (.-ELEMENT_ARRAY_BUFFER gl) indices (.-STATIC_DRAW gl))

                  ;; Setup wireframe index buffer
                  (.bindBuffer gl (.-ELEMENT_ARRAY_BUFFER gl) wireframe-buffer)
                  (.bufferData gl (.-ELEMENT_ARRAY_BUFFER gl) wireframe-indices (.-STATIC_DRAW gl))

                  ;; Initial projection matrix setup (will be updated in render loop)

                  (let [render (fn render [time]
                                 (let [angle (* time 0.001)
                                       model-matrix (create-rotation-matrix angle angle angle)
                                       view-matrix (js/Float32Array. #js [1 0 0 0
                                                                          0 1 0 0
                                                                          0 0 1 0
                                                                          0 0 -6 1])
                                       ;; Calculate projection matrix based on current canvas size
                                       projection-matrix (create-perspective-matrix
                                                          (/ js/Math.PI 4)
                                                          (/ (.-width canvas) (.-height canvas))
                                                          0.1
                                                          100.0)]

                                   (.viewport gl 0 0 (.-width canvas) (.-height canvas))
                                   (.clearColor gl 0.1 0.1 0.1 1.0)
                                   (.clear gl (bit-or (.-COLOR_BUFFER_BIT gl) (.-DEPTH_BUFFER_BIT gl)))
                                   (.enable gl (.-DEPTH_TEST gl))

                                   (.uniformMatrix4fv gl model-location false model-matrix)
                                   (.uniformMatrix4fv gl view-location false view-matrix)
                                   (.uniformMatrix4fv gl projection-location false projection-matrix)

                                   ;; Setup vertex attributes
                                   (.bindBuffer gl (.-ARRAY_BUFFER gl) vertex-buffer)
                                   (.enableVertexAttribArray gl position-location)
                                   (.vertexAttribPointer gl position-location 3 (.-FLOAT gl) false 24 0)
                                   (.enableVertexAttribArray gl color-location)
                                   (.vertexAttribPointer gl color-location 3 (.-FLOAT gl) false 24 12)

                                   ;; Render based on current mode
                                   (case @render-mode
                                     :solid (do
                                              (.bindBuffer gl (.-ELEMENT_ARRAY_BUFFER gl) index-buffer)
                                              (.drawElements gl (.-TRIANGLES gl) 36 (.-UNSIGNED_SHORT gl) 0))

                                     :wireframe (do
                                                  (.bindBuffer gl (.-ELEMENT_ARRAY_BUFFER gl) wireframe-buffer)
                                                  (.drawElements gl (.-LINES gl) 48 (.-UNSIGNED_SHORT gl) 0))

                                     :points (.drawArrays gl (.-POINTS gl) 0 24))

                                   (reset! animation-id (js/requestAnimationFrame render))))]

                    (reset! animation-id (js/requestAnimationFrame render)))))))))

      :component-will-unmount
      (fn [this]
        (when @animation-id
          (js/cancelAnimationFrame @animation-id))
        ;; Remove keyboard event listener
        (when @key-handler
          (.removeEventListener js/document "keydown" @key-handler))
        ;; Remove resize event listener
        (when @resize-handler
          (.removeEventListener js/window "resize" @resize-handler)))

      :reagent-render
      (fn []
        [:canvas {:ref #(reset! canvas-ref %)
                  :width (.-innerWidth js/window)
                  :height (.-innerHeight js/window)
                  :style {:display "block"
                          :width "100vw"
                          :height "100vh"
                          :background "#000"}}])})))

(defn generate-3d-object
  [history]
  (-> (fire/call-function "generate3DObject" history)
      (p/then (fn [result]
                (js/console.log "Generated 3D object:" result)
                result))
      (p/catch (fn [error]
                 (js/console.error "Error generating 3D object:" error)
                 (throw error)))))

(defonce chat-history (atom []))

(js/console.log @chat-history)

(defn handle-generate-object
  [description loading-atom error-atom]
  (reset! loading-atom true)
  (reset! error-atom nil)

  (swap! chat-history conj (str "USER> " description))

  (js/console.log @chat-history)

  (-> (generate-3d-object @chat-history)
      (p/then (fn [result]
                (swap! chat-history conj (str "AI> " (.-data result)))
                (let 
                  [data (js/JSON.parse (.-data result))]
                  (js/console.log "Generated object result:" data)
                  (reset! generated-object data)
                  (reset! loading-atom false))))
                ;; Force re-render by updating a dummy atom or triggering component update
                ;; The WebGL component will pick up the new data on next render
                
      (p/catch (fn [error]
                 (js/console.error "Generation failed:" error)
                 (reset! error-atom (str "Failed to generate object: " (.-message error)))
                 (reset! loading-atom false)))))

(defn show
  []
  (let [input-text (r/atom "")
        loading (r/atom false)
        error-message (r/atom nil)]

    (fn []
      [:div {:class "relative w-screen h-screen overflow-hidden bg-base-100"}
       ;; Fullscreen canvas (key forces re-mount when object changes)
       ^{:key (str "webgl-" (hash @generated-object))} [webgl-cube]

       ;; Top overlay UI elements
       [:div {:class "absolute top-4 left-4 z-10 text-base-content bg-base-200/80 backdrop-blur-sm rounded-lg p-4 shadow-lg"}
        [:h1 {:class "text-2xl font-bold mb-2 text-primary"} "Gen3D WebGL Demo"]
        [:div {:class "text-sm space-y-1"}
         [:p "WebGL-powered rotating 3D cube"]
         [:p "Press keys to switch rendering modes:"]
         [:p
          [:a.cursor-pointer {:on-click #(reset! render-mode :solid)} "1 = Solid triangles"]
          " | "
          [:a.cursor-pointer {:on-click #(reset! render-mode :wireframe)} "2 = Wireframe"]
          " | "
          [:a.cursor-pointer {:on-click #(reset! render-mode :points)} "3 = Points"]]]]

       ;; Bottom input area
       [:div {:class "absolute bottom-4 left-1/2 transform -translate-x-1/2 z-10 w-full max-w-2xl px-4"}
        [:div {:class "bg-base-200/90 backdrop-blur-sm rounded-lg p-4 shadow-lg"}
         [:div {:class "flex flex-col space-y-2"}
          [:label {:class "text-sm font-medium text-base-content"}
           "Describe a 3D object to generate:"]
          [:div {:class "flex space-x-2"}
           [:input {:type "text"
                    :value @input-text
                    :on-change #(reset! input-text (-> % .-target .-value))
                    :on-key-down (fn [e]
                                   (when (= (.-key e) "Enter")
                                     (when-not (empty? @input-text)
                                       (handle-generate-object @input-text loading error-message)
                                       (reset! input-text ""))))
                    :placeholder "e.g., red pyramid, blue house, spiky ball..."
                    :disabled @loading
                    :class "flex-1 input input-bordered input-primary bg-base-100 text-base-content placeholder-base-content/60"}]
           [:button {:on-click (fn []
                                 (when-not (empty? @input-text)
                                   (handle-generate-object @input-text loading error-message)
                                   (reset! input-text "")))
                     :disabled (or @loading (empty? @input-text))
                     :class "btn btn-primary"}
            (if @loading "Generating..." "Generate")]]

          ;; Error message
          (when @error-message
            [:div {:class "alert alert-error text-sm"}
             [:span @error-message]])

          ;; Instructions
          [:p {:class "text-xs text-base-content/70 text-center"}
           "Press Enter or click Generate to create a 3D object from your description"]]]]])))

