(ns sequences.views
    (:require [re-frame.core :as re-frame]
              [leipzig.melody :as melody]
              [leipzig.scale :as scale]
              [leipzig.temperament :as temperament]
              [quil.core :as q :include-macros true]
              [quil.middleware :as middleware]
              [sequences.synthesis :as syn]
              [reagent.core :as reagent]))

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
  (map #(iform %) (filter #(isExtreme? %) (range 0 1000))))

(def checked (transient #{}))

(defn checkInterval
  "Check if an interval is extreme"
  [interval]
  (let [return (and (not (clojure.set/subset? #{interval} checked)) (some #{interval} extremes))]
    (conj! checked interval)
    return))

(defn synth [note]
  (let [gain (re-frame/subscribe [:gain])]
    (syn/connect->
      (syn/add (syn/triangle (* 1.01 (:pitch note))) (syn/sine (:pitch note)))
      ;(syn/low-pass 500)
      (syn/adsr 0.001 0.3 0.2 0.1)
      (syn/gain @gain))))

(def melody
  (->> (melody/phrase (cycle [0.5]) (take 1000 iseries))
       (melody/all :instrument synth)))

(def track 
  (->> melody
    (melody/wherever (comp checkInterval :pitch) :isExtreme? (melody/is true))
    (melody/tempo (melody/bpm 120))
    (melody/where :pitch scale/G)))
  
(defn setup []
  (q/background 0)
  (q/frame-rate 60)
  (q/no-loop))

(defn moveNote [note]
  (let [pitch (:pitch note)
        time (:time note)
        spin (* @(re-frame/subscribe [:spin]) 0.00001)]
    (merge note {:time (- time (* time spin))})))
  
(defn updateNotes [notes]
  (map #(moveNote %) notes))

(defn note->coord [note]
  (let [spin (* @(re-frame/subscribe [:spin]) 0.00001)
        a (+ (:time note (* (q/frame-count) spin)))
        r (/ (* (:pitch note) a) 20)]
    [(+ (/ (q/width) 2) (* r (q/sin a)))
     (+ (/ (q/height) 2) (* r (q/cos a)))]))
   
(defn draw []
  (let [notes (re-frame/subscribe [:notes])]
    (re-frame/dispatch [:updateNotes (updateNotes @notes)])
    (loop [curr (first @notes)
           tail (rest @notes)
           prev nil]
      (let [[x y] (note->coord curr)
            {:keys [pitch time isExtreme?]} curr
            p (/ (* pitch time) 20)
            paint (q/color (q/random 0 100) (q/random 0 100) (q/random 100 200))
            paint2 (q/color (q/random 0 100) (q/random 100 200) (q/random 100 255))
            paint3 (q/color p (q/random 0 100) (q/random p (* p 10)))]
        (q/stroke-join :round)
        (q/stroke paint)
        (when prev
          (let [[x2 y2] (note->coord (moveNote prev))]
            (q/line x y x2 y2)
            (q/no-stroke)))
      (if isExtreme? (q/fill paint2) (q/fill paint3))
      (q/ellipse x y (/ p 10) (/ p 10) ))
      (when (seq tail)
        (recur (first tail)
               (rest tail)
               (moveNote curr))))))
            
(defn tailspin []
  (q/sketch
    :host "canvas"
    :size [700 500]
    :setup setup
    :draw draw
    :middleware [middleware/fun-mode]))

(defn canvas []
  (reagent/create-class
    {:component-did-mount #(tailspin)
     :reagent-render
     (fn []
       [:div [:canvas#canvas]])}))
     
(defn clearBackground []
  (q/with-sketch (q/get-sketch-by-id "canvas")
    (q/background 0)))

(defn main []
  (let [playing? (re-frame/subscribe [:playing?])
        muted? (re-frame/subscribe [:muted?])]
    (fn []
        (if (q/get-sketch-by-id "canvas")
          (q/with-sketch (q/get-sketch-by-id "canvas")
            (if @playing? (q/start-loop) (q/no-loop))))
        [:main
          [:section
            [:div.actions 
              [:div.fields 
                [:label "Spin"]
                [:input {:type "number" :value @(re-frame/subscribe [:spin]) :on-change #(re-frame/dispatch [:updateSpin (.-value (.-target %))])}]
                [:label "Speed"]
                [:input {:type "number" :min 1 :max 10 :value @(re-frame/subscribe [:speed]) :on-change #(re-frame/dispatch [:updateSpeed (.-value (.-target %))])}]]
              [:div.buttons 
                [:button {:on-click #(re-frame/dispatch (if @playing? [:stop] [:start track]))}
                  (if @playing? "Stop" "Infinitize")]
                [:button {:on-click #(clearBackground)} "Clear"]
                [:button {:on-click #(re-frame/dispatch [:mute])} 
                  (if @muted? "Unmute" "Mute")]]]
            [canvas]]
        ]
    )
))
