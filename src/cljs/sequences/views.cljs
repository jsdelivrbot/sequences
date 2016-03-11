(ns sequences.views
    (:require [re-frame.core :as re-frame]
              [leipzig.melody :as melody]
              [leipzig.scale :as scale]
              [leipzig.temperament :as temperament]
              [quil.core :as q :include-macros true]
              [quil.middleware :as middleware]
              [sequences.synthesis :as syn]
              [reagent.core :as reagent]))

;; TODO: Can we unify leipzig/web audio with quil state? With core.async, re-frame? 

(defn intToBin
  "Convert integer to binary string"
  [x]
  (map #(js/parseInt %) (.split (.toString x 2) #"")))

(defn iform
  "Derive the infinity series interval of a particular index in the seq"
  [n]
  (let [bin (intToBin n)]
    (reduce #(if (zero? %2) (* %1 -1) (inc %1)) 0 bin)))

(defn isExtreme? 
  "Check if an interval is 'extreme', i.e. all 1's in binary"
  [idx]
  (every? #(not (zero? %)) (intToBin idx)))
  
(def iseries
  "Generate the infinity series lazily - 0, 1, -1, 2, 1, 0, -2, 3..."
  (map #(iform %) (iterate inc 0)))

(def extremes
  "Generate all extreme intervals"
  (map #(iform %) (filter #(isExtreme? %) (range 0 500))))

(def checked (transient #{}))

(defn checkInterval
  "Check if an interval is extreme"
  [interval]
  (let [return (and (not (clojure.set/subset? #{interval} checked)) (some #{interval} extremes))]
    (conj! checked interval)
    return))

(defn synth [note]
  (syn/connect->
    (syn/add (syn/sawtooth (* 1.01 (:pitch note))) (syn/sawtooth (:pitch note)))
    (syn/low-pass 600)
    (syn/adsr 0.001 0.4 0.2 0.1)
    (syn/gain 0.05)))

(def melody
  (->> (melody/phrase (cycle [0.5]) (take 1000 iseries))
       (melody/all :instrument synth)))

(def track
  (->> melody
    (melody/wherever (comp checkInterval :pitch) :isExtreme? (melody/is true))
    (melody/tempo (melody/bpm 120))
    (melody/where :pitch scale/G)))
  
(defn setup []
  (q/frame-rate 60)
  (q/no-loop)
  (let [notes track
        dots (map #(vec [(/ (* (:pitch %) (:time %)) 20) (* (:time %) 2) (:isExtreme? %)]) notes)]
    {:dots dots
     :notes notes}))

(def speed 0.00001)

(defn move [dot]
  (let [[r a e] dot]
    [r (+ a (* a speed)) e]))

(defn update-state [state]
  (update-in state [:dots] #(map move %)))

(defn dot->coord [[r a]]
  [(+ (/ (q/width) 2) (* r (q/sin a)))
   (+ (/ (q/height) 2) (* r (q/cos a)))])
 
(defn draw-state [state]
  (q/background 0)
  (q/fill 0)
  (let [dots (:dots state)
        offset (* (q/frame-count) speed)
        notes (:notes state)
        sync (re-frame/subscribe [:sync]) 
        playing? (re-frame/subscribe [:playing?]) 
        relative-time (-> (Date.now) (- @sync) (mod (* 1000 (melody/duration notes))) (/ 1000))
        marked (filter #(and @playing? (<= (/ (second %) 2) (+ relative-time (* (/ (second %) 2) offset)))) dots)]
    (if (= (count dots) (count marked)) (q/no-loop))
    (loop [curr (first marked)
           tail (rest marked)
           prev nil]
      (let [[x y] (dot->coord curr)
            [_ _ extreme?] curr
            paint (q/color (q/random 0 100) (q/random 100 200) (q/random 100 200))
            paint2 (q/color (q/random 100 200) (q/random 0 100) (q/random 0 100))]
        (q/stroke-join :round)
        (q/stroke paint)
        (when prev
          (let [[x2 y2] (dot->coord prev)]
            (q/line x y x2 y2)
            (q/no-stroke)
            (q/fill paint)
            (if extreme? (q/fill paint2) (q/fill paint))
            (if extreme? (q/ellipse x y 10 10) (q/ellipse x y 5 5)))))
      (when (seq tail)
        (recur (first tail)
               (rest tail)
               curr)))))
             
(defn tailspin []
  (q/sketch
    :host "canvas"
    :size [700 500]
    :setup setup
    :draw draw-state
    :no-start true
    :update update-state
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
          [:section
            [:button {:on-click #(re-frame/dispatch (if @playing? [:stop] [:start track]))}
              (if @playing? "Stop" "Infinitize")]
            [canvas]]
        ]
    )
))
