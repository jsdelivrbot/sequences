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
    (syn/add (syn/square (* 1.01 (:pitch note))) (syn/sawtooth (:pitch note)))
    (syn/low-pass 600)
    (syn/adsr 0.001 0.4 0.5 0.1)
    (syn/gain 0.05)))

(def melody
  (->> (melody/phrase (cycle [0.5]) (take 100 iseries))
       (melody/all :instrument synth)))

#_(def extremes
  (let [series (take 100 iseries)]
    (take 100 (take-nth #(+ (* % 2) 1) iseries))))

(def track 
  (->> melody
     (melody/tempo (melody/bpm 90))
     (melody/where :pitch scale/G)))

(defn setup []
  (q/frame-rate 60)
  (let [notes track
        dots (map #(vec [(/ (:pitch %) 0.4) (* (:time %) 100)]) notes)]
    {:dots dots}))

(def speed 0.00003)

(defn move [dot]
  (let [[r a] dot]
    [r (+ a (* r speed))]))

(defn update-state [state]
  (update-in state [:dots] #(map move %)))

(defn dot->coord [[r a]]
  [(+ (/ (q/width) 2) (* r (q/cos a)))
   (+ (/ (q/height) 2) (* r (q/sin a)))])

(defn draw-state [state]
  (q/background 255)
  (q/fill 0)
  (let [dots (:dots state)]
    (loop [curr (first dots)
           tail (rest dots)
           prev nil]
      (let [[x y] (dot->coord curr)]
        (q/ellipse x y 5 5)
        (when prev
          (let [[x2 y2] (dot->coord prev)]
            (q/line x y x2 y2))))
      (when (seq tail)
        (recur (first tail)
               (rest tail)
               curr)))))

(defn tailspin []
  (q/sketch 
    :host "graph"
    :size [500 500]
    :setup setup
    :update update-state
    :no-start true
    :draw draw-state
    :middleware [middleware/fun-mode]))

(defn draw-seq []
  (let [[height width] [400 800]
         notes (re-frame/subscribe [:notes])
         sync (re-frame/subscribe [:sync])
         playing? (re-frame/subscribe [:playing?])]
    (q/sketch :draw (fn [_]
                         (q/background 255)
                              (let [relative-time (-> (Date.now) (- @sync) (mod (* 1000 (melody/duration @notes))) (/ 1000))
                                    marked (->> @notes
                                                (melody/wherever
                                                  #(and @playing? (<= (:time %) relative-time))
                                                  :played? (melody/is true)))]
                                (doseq [{:keys [time pitch played?]} marked]
                                  (let [colour (if played? 200 0)
                                        x (* (+ time 1) 20)
                                        y (+ height (- (* pitch 3)))]
                                    (q/stroke colour)
                                    (q/fill colour)
                                    (q/ellipse x y 10 10)))))
                 :host "graph"
                 :no-start true
                 :middleware [middleware/fun-mode]
                 :size [width height])))

(defn canvas [playing?]
  (if playing? 
    (reagent/create-class
      { :component-did-mount #(tailspin)
        :reagent-render 
          (fn []
              [:div {:class "graph"} [:canvas#graph]])})))

(defn main []
  (let [playing? (re-frame/subscribe [:playing?])]
    (fn []
        [:main
          [:h1.title "Sequences"]
          [:button {:on-click #(re-frame/dispatch (if @playing? [:stop] [:start track]))} 
            (if @playing? "Stop" "Start")]
          [canvas @playing?]
        ]
    )
))
