(ns gen3d.viewer.views.home
  (:require
   [reagent.core :as r]
   [reitit.frontend.easy :as rfe]
   [promesa.core :as p]
   [clojure.string :as s]
   ["three" :as THREE]

   [anr.fire.web :as fire]

   [gen3d.viewer.state :refer [state]]))

;; Default object configuration - can be changed to create any 3D object
(defonce object-config-atom
  (r/atom
   {:geometry {:type "CustomGeometry"
               :vertices [;; 8 vertices of a cube
                          [-1 -1 -1] [1 -1 -1] [1 1 -1] [-1 1 -1] ; Back face
                          [-1 -1 1] [1 -1 1] [1 1 1] [-1 1 1]] ; Front face
               :indices [;; 12 triangular faces (2 per cube face) - counter-clockwise winding
                         0 2 1 0 3 2 ; Back face (z = -1)
                         4 5 6 4 6 7 ; Front face (z = +1)
                         0 4 7 0 7 3 ; Left face (x = -1)
                         1 2 6 1 6 5 ; Right face (x = +1)
                         0 1 5 0 5 4 ; Bottom face (y = -1)
                         3 7 6 3 6 2]} ; Top face (y = +1)
    :material {:type "MeshPhongMaterial"
               :parameters {:color 0x0077ff
                            :shininess 100}}
    :position {:x 0 :y 0 :z 0}
    :rotation {:x 0 :y 0 :z 0}
    :scale {:x 1 :y 1 :z 1}}))

;; Predefined object configurations
(def object-presets
  {:pyramid {:geometry {:type "CustomGeometry"
                        :vertices [;; Base vertices (square)
                                   [1 0 1] [-1 0 1] [-1 0 -1] [1 0 -1]
                                   ;; Apex vertex
                                   [0 2 0]]
                        :indices [;; Base faces (2 triangles for square)
                                  0 1 2 0 2 3
                                  ;; Side faces
                                  0 4 1 1 4 2 2 4 3 3 4 0]}
             :material {:type "MeshPhongMaterial" :parameters {:color 0xffaa00 :shininess 100}}
             :position {:x 0 :y 0 :z 0} :rotation {:x 0 :y 0 :z 0} :scale {:x 1 :y 1 :z 1}}

   :tetrahedron {:geometry {:type "CustomGeometry"
                            :vertices [;; 4 vertices of tetrahedron
                                       [1 1 1] [-1 -1 1] [-1 1 -1] [1 -1 -1]]
                            :indices [;; 4 triangular faces - counter-clockwise winding
                                      0 2 1 0 3 2 0 1 3 1 2 3]}
                 :material {:type "MeshPhongMaterial" :parameters {:color 0x00ffaa :shininess 100}}
                 :position {:x 0 :y 0 :z 0} :rotation {:x 0 :y 0 :z 0} :scale {:x 1 :y 1 :z 1}}

   :diamond {:geometry {:type "CustomGeometry"
                        :vertices [;; Top and bottom points (elongated like a gem)
                                   [0 2.2 0] [0 -2.2 0]
                                   ;; Upper middle ring (smaller)
                                   [0.6 0.8 0] [0 0.8 0.6] [-0.6 0.8 0] [0 0.8 -0.6]
                                   ;; Lower middle ring (smaller)  
                                   [0.6 -0.8 0] [0 -0.8 0.6] [-0.6 -0.8 0] [0 -0.8 -0.6]]
                        :indices [;; Top pyramid faces
                                  0 3 2 0 4 3 0 5 4 0 2 5
                                  ;; Upper belt faces
                                  2 3 7 2 7 6 3 4 8 3 8 7 4 5 9 4 9 8 5 2 6 5 6 9
                                  ;; Bottom pyramid faces
                                  1 6 7 1 7 8 1 8 9 1 9 6]}
             :material {:type "MeshPhongMaterial" :parameters {:color 0xff3366 :shininess 100}}
             :position {:x 0 :y 0 :z 0} :rotation {:x 0 :y 0 :z 0} :scale {:x 1 :y 1 :z 1}}

   :cube {:geometry {:type "CustomGeometry"
                     :vertices [;; 8 vertices of a cube
                                [-1 -1 -1] [1 -1 -1] [1 1 -1] [-1 1 -1] ; Back face
                                [-1 -1 1] [1 -1 1] [1 1 1] [-1 1 1]] ; Front face
                     :indices [;; 12 triangular faces (2 per cube face) - counter-clockwise winding
                               ;; Back face (z = -1)
                               0 2 1 0 3 2
                               ;; Front face (z = +1)
                               4 5 6 4 6 7
                               ;; Left face (x = -1)
                               0 4 7 0 7 3
                               ;; Right face (x = +1)
                               1 2 6 1 6 5
                               ;; Bottom face (y = -1)
                               0 1 5 0 5 4
                               ;; Top face (y = +1)
                               3 7 6 3 6 2]}
          :material {:type "MeshPhongMaterial" :parameters {:color 0x0077ff :shininess 100}}
          :position {:x 0 :y 0 :z 0} :rotation {:x 0 :y 0 :z 0} :scale {:x 1 :y 1 :z 1}}

   :octahedron {:geometry {:type "CustomGeometry"
                           :vertices [;; 6 vertices of regular octahedron
                                      [0 1.5 0] [0 -1.5 0] ; Top and bottom
                                      [1 0 0] [0 0 1] [-1 0 0] [0 0 -1]] ; Middle ring
                           :indices [;; 8 triangular faces (counter-clockwise from outside)
                                     ;; Top faces
                                     0 3 2 0 4 3 0 5 4 0 2 5
                                     ;; Bottom faces  
                                     1 2 3 1 3 4 1 4 5 1 5 2]}
                :material {:type "MeshPhongMaterial" :parameters {:color 0x9966ff :shininess 100}}
                :position {:x 0 :y 0 :z 0} :rotation {:x 0 :y 0 :z 0} :scale {:x 1 :y 1 :z 1}}})

;; Generation state for cloud function calls
(defonce generation-state (r/atom {:loading false :error nil :description ""}))

(defonce cube-atom (r/atom nil))

(defn get-cube
  "Get the current cube object"
  []
  @cube-atom)

(defn set-cube!
  "Set a new cube object"
  [new-cube]
  (reset! cube-atom new-cube))

(defn update-cube!
  "Update the cube object with a function"
  [update-fn & args]
  (apply swap! cube-atom update-fn args))

(defn create-custom-buffer-geometry
  "Create a BufferGeometry from vertices, indices, and optional normals/uvs"
  [{:keys [vertices indices normals uvs]}]
  (let [geometry (THREE/BufferGeometry.)]

    ;; Set vertices (required)
    (when vertices
      (let [vertex-array (js/Float32Array. (clj->js (flatten vertices)))]
        (.setAttribute geometry "position" (THREE/BufferAttribute. vertex-array 3))))

    ;; Set indices (optional, for face definitions)
    (when indices
      (let [index-array (js/Uint16Array. (clj->js indices))]
        (.setIndex geometry (THREE/BufferAttribute. index-array 1))))

    ;; Set normals (optional, will be computed if not provided)
    (if normals
      (let [normal-array (js/Float32Array. (clj->js (flatten normals)))]
        (.setAttribute geometry "normal" (THREE/BufferAttribute. normal-array 3)))
      (.computeVertexNormals geometry))

    ;; Set UVs (optional, for texture mapping)
    (when uvs
      (let [uv-array (js/Float32Array. (clj->js (flatten uvs)))]
        (.setAttribute geometry "uv" (THREE/BufferAttribute. uv-array 2))))

    geometry))

(defn create-geometry-from-config
  "Create a three.js geometry from configuration - only supports CustomGeometry"
  [{:keys [type vertices indices normals uvs]}]
  (case type
    "CustomGeometry"
    (create-custom-buffer-geometry {:vertices vertices
                                    :indices indices
                                    :normals normals
                                    :uvs uvs})

    ;; Default fallback - create a simple cube if type is not recognized
    (create-custom-buffer-geometry
     {:vertices [[-1 -1 -1] [1 -1 -1] [1 1 -1] [-1 1 -1]
                 [-1 -1 1] [1 -1 1] [1 1 1] [-1 1 1]]
      :indices [0 1 2 0 2 3 4 6 5 4 7 6
                0 3 7 0 7 4 1 5 6 1 6 2
                0 4 5 0 5 1 3 2 6 3 6 7]})))

(defn create-material-from-config
  "Create a three.js material from configuration"
  [{:keys [type parameters]}]
  (case type
    "MeshPhongMaterial"
    (let [material (THREE/MeshPhongMaterial. (clj->js parameters))]
      (set! (.-flatShading material) true)
      material)

    "MeshBasicMaterial"
    (let [material (THREE/MeshBasicMaterial. (clj->js parameters))]
      (set! (.-flatShading material) true)
      material)

    "MeshStandardMaterial"
    (let [material (THREE/MeshStandardMaterial. (clj->js parameters))]
      (set! (.-flatShading material) true)
      material)

    "MeshLambertMaterial"
    (let [material (THREE/MeshLambertMaterial. (clj->js parameters))]
      (set! (.-flatShading material) true)
      material)

    ;; Default fallback
    (let [material (THREE/MeshPhongMaterial. #js {:color 0x0077ff})]
      (set! (.-flatShading material) true)
      material)))

(defn create-object-from-config
  "Create a complete 3D object from JSON configuration"
  [config]
  (let [geometry (create-geometry-from-config (:geometry config))
        material (create-material-from-config (:material config))
        mesh (THREE/Mesh. geometry material)
        {:keys [position rotation scale]} config]

    ;; Set position
    (when position
      (.set (.-position mesh) (:x position) (:y position) (:z position)))

    ;; Set rotation
    (when rotation
      (set! (.-x (.-rotation mesh)) (:x rotation))
      (set! (.-y (.-rotation mesh)) (:y rotation))
      (set! (.-z (.-rotation mesh)) (:z rotation)))

    ;; Set scale
    (when scale
      (.set (.-scale mesh) (:x scale) (:y scale) (:z scale)))

    mesh))

(defn set-object-config!
  "Set a new object configuration and recreate the object"
  [new-config]
  (reset! object-config-atom new-config))

(defn load-preset!
  "Load a predefined object preset"
  [preset-key]
  (when-let [preset (get object-presets preset-key)]
    (set-object-config! preset)))

(defn generate-object-from-description!
  "Call cloud function to generate 3D object from text description"
  [description]
  (when (and description (not (clojure.string/blank? description)))
    (swap! generation-state assoc :loading true :error nil)
    (-> (fire/call-function "generate3DObject" description)
        (p/then (fn [result]
                  (swap! generation-state assoc :loading false :description "")
                  (when-let [object-config (js->clj (js/JSON.parse (.-data result)) :keywordize-keys true)]
                    (js/console.log "Object generation result:" object-config)
                    (set-object-config! object-config))))
        (p/catch (fn [error]
                   (swap! generation-state assoc :loading false :error (str "Error: " (.-message error)))
                   (js/console.error "Failed to generate object:" error))))))

(defn create-custom-object!
  "Example of creating a custom object via JSON configuration"
  []
  (set-object-config!
   {:geometry {:type "CustomGeometry"
               ;; Define vertices for a simple house shape
               :vertices [;; Base square
                          [-1 -1 -1] [1 -1 -1] [1 -1 1] [-1 -1 1]
                          ;; Top square
                          [-1 1 -1] [1 1 -1] [1 1 1] [-1 1 1]
                          ;; Roof peak
                          [0 2 -1] [0 2 1]]
               ;; Define faces using vertex indices
               :indices [;; Bottom face
                         0 1 2 0 2 3
                         ;; Top face (flat roof part)
                         4 7 6 4 6 5
                         ;; Walls
                         0 4 5 0 5 1 ; Front wall
                         1 5 6 1 6 2 ; Right wall
                         2 6 7 2 7 3 ; Back wall
                         3 7 4 3 4 0 ; Left wall
                         ;; Roof triangles
                         4 8 5 5 8 6 6 8 7 7 8 4
                         6 9 7 7 9 4 4 9 8 8 9 6]}
    :material {:type "MeshPhongMaterial"
               :parameters {:color 0xffd700 ; Gold color
                            :shininess 50}}
    :position {:x 0 :y 0 :z 0}
    :rotation {:x 0 :y 0 :z 0}
    :scale {:x 1 :y 1 :z 1}}))

(defn create-star-object!
  "Create a 3D star using custom vertices"
  []
  (set-object-config!
   {:geometry {:type "CustomGeometry"
               :vertices [;; Center point
                          [0 0 0]
                          ;; Star points (8-pointed star)
                          [2 0 0] [1.4 1.4 0] [0 2 0] [-1.4 1.4 0]
                          [-2 0 0] [-1.4 -1.4 0] [0 -2 0] [1.4 -1.4 0]
                          ;; Inner points
                          [0.7 0 0] [0.5 0.5 0] [0 0.7 0] [-0.5 0.5 0]
                          [-0.7 0 0] [-0.5 -0.5 0] [0 -0.7 0] [0.5 -0.5 0]]
               :indices [;; Connect center to all points
                         0 1 2 0 2 3 0 3 4 0 4 5
                         0 5 6 0 6 7 0 7 8 0 8 1
                         ;; Create star pattern
                         1 9 10 2 10 11 3 11 12 4 12 13
                         5 13 14 6 14 15 7 15 16 8 16 9]}
    :material {:type "MeshPhongMaterial"
               :parameters {:color 0xff6b00 :shininess 80}}
    :position {:x 0 :y 0 :z 0}
    :rotation {:x 0 :y 0 :z 0}
    :scale {:x 1 :y 1 :z 1}}))

(defn change-cube-color!
  "Change the cube's material color"
  [color]
  (when-let [cube @cube-atom]
    (set! (.-color (.-material cube)) (THREE/Color. color))))

(defn change-cube-scale!
  "Change the cube's scale"
  [scale]
  (when-let [cube @cube-atom]
    (let [scale-obj (.-scale cube)]
      (.setScalar scale-obj scale))))

(defn reset-cube-rotation!
  "Reset the cube's rotation to zero"
  []
  (when-let [cube @cube-atom]
    (set! (.-x (.-rotation cube)) 0)
    (set! (.-y (.-rotation cube)) 0)
    (set! (.-z (.-rotation cube)) 0)))

(defn rotating-cube
  "Component that creates a rotating 3D object using three.js and JSON configuration"
  []
  (let [canvas-ref (r/atom nil)
        animation-id (r/atom nil)
        scene-atom (r/atom nil)
        camera-atom (r/atom nil)
        renderer-atom (r/atom nil)]

    (r/create-class
     {:component-did-mount
      (fn [this]
        (when-let [canvas @canvas-ref]
          (let [scene (THREE/Scene.)
                camera (THREE/PerspectiveCamera. 75 (/ js/window.innerWidth js/window.innerHeight) 0.1 1000)
                renderer (THREE/WebGLRenderer. #js {:canvas canvas :antialias true})
                light (THREE/DirectionalLight. 0xffffff 0.3)]

            ;; Store objects for resize handling
            (reset! scene-atom scene)
            (reset! camera-atom camera)
            (reset! renderer-atom renderer)

            ;; Create initial object from config
            (let [object (create-object-from-config @object-config-atom)]
              (set-cube! object)
              (.add scene object))

            (.setSize renderer js/window.innerWidth js/window.innerHeight false)
            (.setPixelRatio renderer js/window.devicePixelRatio)

            (.set (.-position light) 5 5 5)
            (.add scene light)
            (.add scene (THREE/AmbientLight. 0x404040 0.9))

            (.set (.-position camera) 0 0 5)

            ;; Window resize handler
            ;; Window resize handler
            (let [handle-resize (fn []
                                  (when (and @camera-atom @renderer-atom)
                                    (let [width js/window.innerWidth
                                          height js/window.innerHeight]
                                     ;; Update camera aspect ratio
                                      (set! (.-aspect @camera-atom) (/ width height))
                                      (.updateProjectionMatrix @camera-atom)
                                     ;; Update renderer size
                                      (.setSize @renderer-atom width height false)
                                      (.setPixelRatio @renderer-atom js/window.devicePixelRatio))))]

              ;; Add resize event listener
              (.addEventListener js/window "resize" handle-resize)

              ;; Store handler for cleanup
              (set! (.-resize-handler this) handle-resize))

            ;; Watch for config changes and recreate object
            (add-watch object-config-atom :object-recreator
                       (fn [key atom old-state new-state]
                         (when (and @scene-atom @cube-atom)
                  ;; Remove old object
                           (.remove @scene-atom @cube-atom)
                  ;; Create and add new object
                           (let [new-object (create-object-from-config new-state)]
                             (set-cube! new-object)
                             (.add @scene-atom new-object)))))

            (let [animate (fn animate []
                            (reset! animation-id (js/requestAnimationFrame animate))
                           ;; Use global cube atom for animation
                            (when-let [current-cube @cube-atom]
                              (set! (.-x (.-rotation current-cube)) (+ (.-x (.-rotation current-cube)) 0.01))
                              (set! (.-y (.-rotation current-cube)) (+ (.-y (.-rotation current-cube)) 0.01)))
                            (.render renderer scene camera))]
              (animate)))))

      :component-will-unmount
      (fn [this]
        (when @animation-id
          (js/cancelAnimationFrame @animation-id))
        ;; Remove resize event listener
        (when (.-resize-handler this)
          (.removeEventListener js/window "resize" (.-resize-handler this)))
        ;; Remove config watcher
        (remove-watch object-config-atom :object-recreator)
        ;; Clear atoms
        (set-cube! nil)
        (reset! scene-atom nil)
        (reset! camera-atom nil)
        (reset! renderer-atom nil))

      :reagent-render
      (fn []
        [:div.w-full.h-full.bg-gray-900
         [:canvas {:ref #(reset! canvas-ref %)
                   :class "block w-full h-full"
                   :style {:display "block"}}]])})))

(defn show
  []
  (let [state @generation-state]
    [:div.w-full.h-screen.overflow-hidden.bg-gray-900
     [:div.absolute.top-0.left-0.right-0.z-10.text-center.p-4.bg-gray-900.bg-opacity-90
      [:h1.text-3xl.font-bold.text-white.mb-4 "Custom 3D Objects Demo"]
      [:p.text-gray-300.mb-4 "Generate objects with AI or choose from presets"]

      ;; AI Generation Input
      [:div.mb-6
       [:h3.text-white.mb-2 "AI Object Generation"]
       [:div.flex.gap-2.justify-center.items-center.max-w-md.mx-auto
        [:input.input.input-bordered.flex-1
         {:type "text"
          :placeholder "Describe an object (e.g., 'a spiky star', 'an elegant vase')"
          :value (:description state)
          :disabled (:loading state)
          :on-change #(swap! generation-state assoc :description (-> % .-target .-value))
          :on-key-press #(when (= (.-key %) "Enter")
                           (generate-object-from-description! (:description state)))}]
        [:button.btn.btn-primary
         {:disabled (or (:loading state) (clojure.string/blank? (:description state)))
          :on-click #(generate-object-from-description! (:description state))}
         (if (:loading state)
           [:span.loading.loading-spinner.loading-sm]
           "Generate")]]

       ;; Error message
       (when (:error state)
         [:div.alert.alert-error.mt-2.max-w-md.mx-auto
          [:span (:error state)]])]

      ;; Custom object selection
      [:div.mb-4
       [:h3.text-white.mb-2 "Preset Objects"]
       [:div.flex.gap-2.justify-center.flex-wrap
        [:button.btn.btn-primary.btn-sm
         {:on-click #(load-preset! :cube)}
         "Cube"]
        [:button.btn.btn-warning.btn-sm
         {:on-click #(load-preset! :pyramid)}
         "Pyramid"]
        [:button.btn.btn-success.btn-sm
         {:on-click #(load-preset! :tetrahedron)}
         "Tetrahedron"]
        [:button.btn.btn-error.btn-sm
         {:on-click #(load-preset! :diamond)}
         "Diamond"]
        [:button.btn.btn-secondary.btn-sm
         {:on-click #(load-preset! :octahedron)}
         "Octahedron"]]]

      ;; Color controls (work with any object type)
      [:div.mb-4
       [:h3.text-white.mb-2 "Controls"]
       [:div.flex.gap-2.justify-center.flex-wrap
        [:button.btn.btn-error.btn-sm
         {:on-click #(change-cube-color! 0xff0000)}
         "Red"]
        [:button.btn.btn-success.btn-sm
         {:on-click #(change-cube-color! 0x00ff00)}
         "Green"]
        [:button.btn.btn-info.btn-sm
         {:on-click #(change-cube-color! 0x0077ff)}
         "Blue"]
        [:button.btn.btn-ghost.btn-sm
         {:on-click #(do (change-cube-scale! 1.0) (reset-cube-rotation!))}
         "Reset"]]]]

     ;; Canvas fills the full screen
     [:div.absolute.inset-0
      [rotating-cube]]]))
