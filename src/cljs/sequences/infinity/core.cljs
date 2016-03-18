(ns sequences.infinity.core
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
  (->> (melody/phrase (cycle [1]) (take 800 iseries))
       (melody/all :instrument synth)))

(def track 
  (->> melody
    (melody/wherever (comp checkInterval :pitch) :isExtreme? (melody/is true))
    (melody/tempo (melody/bpm 120))
    (melody/where :pitch scale/G)))
  
(defn setup []
  (q/background 0)
  (q/frame-rate 60))

(defn note->coord [note]
  (let [spin (* @(re-frame/subscribe [:spin]) 0.0001)
        a (+ (:time note) (* (q/frame-count) spin))
        r (/ (* (:pitch note) a) 20)]
    [(+ (/ (q/width) 2) (* r (q/sin a)))
     (+ (/ (q/height) 2) (* r (q/cos a)))]))
   
(defn draw []
  (let [notes (re-frame/subscribe [:notes])]
    (doseq [note @notes]
      (let [[x y] (note->coord note)
            {:keys [pitch time isExtreme?]} note
            p (/ (* pitch time) 40)
            paint (q/color (q/random 0 100) (q/random 100 200) (q/random 100 255))
            paint2 (q/color p (q/random 0 100) (q/random p (* p 10)))]
      (q/no-stroke)
      (if isExtreme? (q/fill paint) (q/fill paint2))
      (q/ellipse x y (/ p 10) (/ p 10) )))))
            
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
  
(defn atomize []
  (re-frame/dispatch [:updateNotes [[]]]))
  
(defn main []
  (let [playing? (re-frame/subscribe [:playing?])
        muted? (re-frame/subscribe [:muted?])
        notes (re-frame/subscribe [:notes])]
    (fn []
        [:main
          [:section
           [:h1 "Infinity Series"]
            [:div.actions 
              [:div.fields 
                [:label "Spin"]
                [:input {:type "number" :min -600 :max 600 :value @(re-frame/subscribe [:spin]) :on-change #(re-frame/dispatch [:updateSpin (.-value (.-target %))])}]
                [:input {:type "range" :min -600 :max 600 :value @(re-frame/subscribe [:spin]) :on-change #(re-frame/dispatch [:updateSpin (.-value (.-target %))])}]
                [:label "Initial Tempo"]
                [:input {:type "number" :min 1 :max 10 :value @(re-frame/subscribe [:speed]) :on-change #(re-frame/dispatch [:updateSpeed (.-value (.-target %))])}]]
              #_[:div [:label (count @notes) "/" (count track)]]
              [:div.buttons 
                [:button {:on-click #(re-frame/dispatch (if @playing? [:stop] [:start track]))}
                  (if @playing? "Stop" "Infinitize")]
                [:button {:on-click #(atomize)} "Atomize"]
                [:button {:on-click #(clearBackground)} "Clear"]
                [:button {:on-click #(re-frame/dispatch [:mute])} 
                  (if @muted? "Unmute" "Mute")]]]
            [canvas]]
        ]
    )
))
