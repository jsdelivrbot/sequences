(ns sequences.newtonian.core
  (:require [reagent.core :as reagent]
            [sequences.newtonian.particle-system :as newt]
            [sequences.newtonian.utils :as utils :refer [Vector2D]]
            [quil.core :as q :include-macros true]
            [quil.middleware :as middleware]))

(defn setup []
  (q/frame-rate 60)
  (q/background 0)
  (reset! newt/fields [])
  (reset! newt/particles [])
  (reset! newt/emitters [])
  (newt/add-emitter (Vector2D. 350.0 250.0) (Vector2D. 1 5.5))
  (newt/add-field (Vector2D. 350.0 300.0) 3000.0))

(defn before-update [])

(defn update-state []
  (newt/add-new-particles)
  (newt/update-particles 700 500))

(defn draw-field [{:keys [position]}]
  (let [x (:x position)
        y (:y position)]
    (q/no-stroke)
    ;(q/fill-int (q/color 255 64 64))
    (q/fill-int 0)
    (q/ellipse x y 5 5)))

(defn draw-particle [{:keys [position velocity accel]}]
  (let [x (:x position)
        y (:y position)
        a (utils/mag accel)
        s (utils/mag velocity)]
    (q/no-stroke)
    (q/fill-int (q/color
               166
               (/ (* 220 0.5) s)
               (* 76 s)
               255))
    (q/ellipse x y 2 2)))

(defn draw []
  (q/background 0)
  (let [particles @newt/particles
        fields @newt/fields]
   (doseq [p particles]
     (draw-particle p))
   (doseq [f fields]
     (draw-field f))))

(defn after-draw [])

(defn main-loop []
  (before-update)
  (update-state)
  (draw)
  (after-draw))

(defn newtonian []
  (q/sketch
    :host "canvas"
    :setup setup
    :draw main-loop
    :size [700 500]
    :middleware [middleware/fun-mode]))

(defn canvas []
  (reagent/create-class
    {:component-did-mount #(newtonian)
     :reagent-render
     (fn []
       [:div [:canvas#canvas]])}))
     
(defn main []
    (fn []
        [:main
          [:section
           [:h1 "Particles"]
            [:div.actions]
            [:div.fields]]
          [canvas]]))