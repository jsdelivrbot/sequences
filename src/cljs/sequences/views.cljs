(ns sequences.views
    (:require [re-frame.core :as re-frame]
              [leipzig.melody :as melody]
              [leipzig.scale :as scale]
              [leipzig.temperament :as temperament]
              [quil.core :as q :include-macros true]
              [quil.middleware :as middleware]
              [sequences.synthesis :as syn]
              [reagent.core :as reagent]))

(defn iform
  "Derive the infinity series interval of a particular index in the seq"
  [n]
  (let [bin (map #(js/parseInt %) (.split (.toString n 2) #""))]
    (reduce #(if (zero? %2) (* %1 -1) (inc %1)) 0 bin)))

(def iseries
  "Generate the infinity series lazily - 0, 1, -1, 2, 1, 0, -2, 3..."
  (map #(iform %) (iterate inc 0)))

(defn synth [note]
  (syn/connect->
    (syn/add (syn/sawtooth (* 1.01 (:pitch note))) (syn/sawtooth (:pitch note)))
    (syn/low-pass 600)
    (syn/adsr 0.001 0.4 0.5 0.9)
    (syn/gain 0.05)))

(def melody
  (->> (melody/phrase (cycle [0.5]) (take 500 iseries))
       (melody/all :instrument synth)))

(def track
  (->> melody
     (melody/tempo (melody/bpm 100))
     (melody/where :pitch scale/G)))

(defn setup []
  (q/frame-rate 30)
  (q/no-loop)
  (let [notes track
        dots (map #(vec [(/ (* (:pitch %) (:time %)) 20) (* (:time %) 2)]) notes)]
    {:dots dots
     :notes notes}))

(def speed 0.0002)

(defn move [dot]
  (let [[r a] dot]
    [r (+ a (* a speed))]))

(defn update-state [state]
  (update-in state [:dots] #(map move %)))

(defn dot->coord [[r a]]
  [(+ (/ (q/width) 2) (* r (q/sin a)))
   (+ (/ (q/height) 2) (* r (q/cos a)))])

(defn draw-state [state]
  (q/background 255)
  (q/fill 0)
  (let [dots (:dots state)
        notes (:notes state)
        sync (re-frame/subscribe [:sync])
        playing? (re-frame/subscribe [:playing?])
        relative-time (-> (Date.now) (- @sync) (mod (* 1000 (melody/duration notes))) (/ 1000))
        marked (map #(if (and @playing? (<= (/ (first (rest %)) 10) relative-time)) (conj % :played) %) dots)]
    (loop [curr (first marked)
           tail (rest marked)
           prev nil]
      (let [[x y] (dot->coord curr)
            paint (if (= (last curr) :played) (q/color (q/random 0 100) (q/random 100 200) (q/random 100 255)) 255)]
        (q/stroke paint)
        (q/fill paint)
        (when prev
          (let [[x2 y2] (dot->coord prev)]
            (q/line x y x2 y2))))
      (when (seq tail)
        (recur (first tail)
               (rest tail)
               curr)))))
             
(defn tailspin []
  (q/sketch
    :host "canvas"
    :size [700 500]
    :setup setup
    :update update-state
    :draw draw-state
    :middleware [middleware/fun-mode]))

(defn canvas []
  (reagent/create-class
    {:component-did-mount #(tailspin)
     :reagent-render
     (fn []
       [:div [:canvas#canvas]])}))

(defn main []
  (let [playing? (re-frame/subscribe [:playing?])]
    (fn []
        (if (q/get-sketch-by-id "canvas")
          (q/with-sketch (q/get-sketch-by-id "canvas")
            (if @playing? (q/start-loop) (q/no-loop))))
        [:main
          [:h1.title "Sequences"]
          [:button {:on-click #(re-frame/dispatch (if @playing? [:stop] [:start track]))}
            (if @playing? "Stop" "Start")]
          [canvas]
        ]
    )
))
