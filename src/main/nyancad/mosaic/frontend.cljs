(ns nyancad.mosaic.frontend
  (:require [reagent.core :as r]
            [reagent.dom :as rd]))

(def grid-size 50)

(defonce state
  (r/atom
   {
    ::zoom [0 0 500 500],
    ::schematic {
                 :mos1 {:x (+ 0  0), :y (+ 0 0), :r 270, :cell :pmos}
                 :mos2 {:x (+ 2 0), :y (+ 1 0), :r 0, :cell :nmos}
                 :mos3 {:x (+ 1 0), :y (+ 3 0), :r 90, :cell :pmos}
                 :mos4 {:x (+ -1 0), :y (+ 2 0), :r 180, :cell :nmos}
                 :mos5 {:x (+ 0  4), :y (+ 0 0), :r 270, :cell :pmos}
                 :mos6 {:x (+ 2 4), :y (+ 1 0), :r 0, :cell :nmos}
                 :mos7 {:x (+ 1 4), :y (+ 3 0), :r 90, :cell :pmos}
                 :mos8 {:x (+ -1 4), :y (+ 2 0), :r 180, :cell :nmos}
                 :mos1a {:x (+ 0  0), :y (+ 0 4), :r 270, :cell :pmos}
                 :mos2a {:x (+ 2 0), :y (+ 1 4), :r 0, :cell :nmos}
                 :mos3a {:x (+ 1 0), :y (+ 3 4), :r 90, :cell :pmos}
                 :mos4a {:x (+ -1 0), :y (+ 2 4), :r 180, :cell :nmos}
                 :mos5a {:x (+ 0  4), :y (+ 0 4), :r 270, :cell :pmos}
                 :mos6a {:x (+ 2 4), :y (+ 1 4), :r 0, :cell :nmos}
                 :mos7a {:x (+ 1 4), :y (+ 3 4), :r 90, :cell :pmos}
                 :mos8a {:x (+ -1 4), :y (+ 2 4), :r 180, :cell :nmos}
    }}))

(defn sign [n] (if (> n 0) 1 -1))

(defn viewbox-coord [e]
  (let [^js el (.-currentTarget e)
        p (.fromPoint js/DOMPoint (clj->js {:x (.-clientX e) :y (.-clientY e)}))
        m (.inverse (.getScreenCTM el))
        tp (.matrixTransform p m)]
    [(.-x tp) (.-y tp)]))

(defn viewbox-movement [e]
  (let [^js el (.-currentTarget e)
        m (.inverse (.getScreenCTM el))
        _ (do (set! (.-e m) 0)
              (set! (.-f m) 0)) ; cancel translation
        ^js p (.fromPoint js/DOMPoint (clj->js {:x (.-movementX e) :y (.-movementY e)}))
        tp (.matrixTransform p m)] ; local movement
    [(.-x tp) (.-y tp)]))

(defn zoom-schematic [e]
  (swap! state update-in [::zoom]
    (fn [[x y w h]]
      (let [dx (* (sign (.-deltaY e)) w 0.1)
            dy (* (sign (.-deltaY e)) h 0.1)
            [ex ey] (viewbox-coord e)
            rx (/ (- ex x) w)
            ry (/ (- ey y) h)]
        [(- x (* dx rx))
         (- y (* dy ry))
         (+ w dx)
         (+ h dy)]))))

(defn drag [e]
  (if (= ::view (::dragging @state))
    (swap! state update-in [::zoom]
           (fn [[x y w h]]
             (let [[dx dy] (viewbox-movement e)]
               [(- x dx)
                (- y dy)
                w h])))
    (when-let [k (::dragging @state)]
    (swap! state (fn [st]
      (update-in st [::schematic k] (fn [d]
        (let [[x y] (map #(/ % grid-size) (viewbox-coord e))]
          (assoc d
                 :x (- x (::offsetx st))
                 :y (- y (::offsety st)))))))))))

(defn tetris [pattern]
  [:g
  (for [[y s] (map-indexed #(vector (* grid-size %1) %2) pattern)
        [x c] (map-indexed #(vector (* grid-size %1) %2) s)
        :when (not= c " ")]
    [:rect.tetris {:x x, :y y, :width grid-size, :height grid-size, :key [x y]}])])

(defn ports [pattern]
  [:g
  (for [[y s] (map-indexed #(vector (* grid-size %1) %2) pattern)
        [x c] (map-indexed #(vector (* grid-size %1) %2) s)
        :when (not= c " ")]
    [:circle.port {:cx (+ x (/ grid-size 2)), :cy (+ y (/ grid-size 2)), :r (/ grid-size 10), :key [x y]}])])

(defn lines [arcs]
  [:g
   (for [arc arcs]
     ^{:key arc} [:polyline {:points (map #(* % grid-size) (flatten arc))}])])

(defn arrow [x y size]
   [:polygon.arrow {:points 
     (map #(* % grid-size)
      [x y,
       (+ x size) (+ y size)
       (+ x size) (- y size)])}])

(defn drag-start-device [k v e]
  (when (= (.-button e) 0)
    (let [[x y] (map #(/ % grid-size) (viewbox-coord e))]
      (swap! state assoc
             ::dragging k
             ::offsetx (- x (:x v))
             ::offsety (- y (:y v))))))

(defn drag-end [e]
  (swap! state
    (fn [st]
      (if-let [target (::dragging st)]
        (if (= target ::view)
          (assoc st ::dragging nil)
          (-> st
              (assoc ::dragging nil)
              (update-in [::schematic target :x] #(.round js/Math %))
              (update-in [::schematic target :y] #(.round js/Math %))))
        st))))

(defn device [size shape conn k v & elements]
  [:svg.device {:x (* (:x v) grid-size)
                :y (* (:y v) grid-size)
                :width (* size grid-size)
                :height (* size grid-size)
                :class [(:cell v) (symbol (str "r" (:r v))) (when (= k (::selected @state)) :selected)]
                :on-mouse-down #(drag-start-device k v %)
                ;; :on-mouse-up #(drag-end-device k %)
                :on-mouse-move drag
                :on-click #(swap! state assoc ::selected k)}
   [:g {:width (* size grid-size)
        :height (* size grid-size)}
    [tetris shape]
    (into [:<>] elements) ; avoid :key warning
    [ports conn]]])

(defn mosfet [k v]
  (let [shape [" #"
               "##"
               " #"]
        conn [" #"
              "##"
              " #"]
        arcs [[[0.5 1.5]
               [1 1.5]]
              [[1 1]
               [1 2]]
              [[1.5 0.5]
               [1.5 1]
               [1.1 1]
               [1.1 2]
               [1.5 2]
               [1.5 2.5]]
              [[1.5 1.5]
               [1.1 1.5]]]]
    [device 3 shape conn k v
     [lines arcs]
     [arrow 1.2 1.5 0.15]]))

(defn schematic-canvas []
  [:svg {:xmlns "http://www.w3.org/2000/svg"
         :height "100%"
         :width "100%"
         :view-box (::zoom @state)
         :on-wheel zoom-schematic
         :on-click #(when (= (.-target %) (.-currentTarget %)) (swap! state assoc ::selected nil))
         :on-mouse-down #(when (= (.-button %) 1) (swap! state assoc ::dragging ::view))
         :on-mouse-up drag-end
         :on-mouse-move drag}
   (for [[k v] (::schematic @state)]
     ^{:key k} [mosfet k v])
   ])

(defn ^:dev/after-load init []
  (rd/render [schematic-canvas]
             (.getElementById js/document "root")))