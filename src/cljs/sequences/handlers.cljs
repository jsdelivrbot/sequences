(ns sequences.handlers
  (:require [re-frame.core :as re-frame]
            [cljs.core.async :refer [chan close! timeout]]
            [sequences.synthesis :as syn]
            [leipzig.temperament :as temperament]
            [leipzig.melody :as melody]
            [sequences.db :as db])
  (:require-macros [cljs.core.async.macros :as m :refer [go]]))

;;¯\_(ツ)_/¯
(defn audio-context
  "Construct an audio context in a way that works even if it's prefixed."
  []
  (if js/window.AudioContext. ; Some browsers e.g. Safari don't use the unprefixed version yet.
    (js/window.AudioContext.)
    (js/window.webkitAudioContext.)))

(defn playNote! [audiocontext note]
  (let [{:keys [duration instrument]} note
        at (.-currentTime audiocontext)
        synth-instance (-> note
                             (update :pitch temperament/equal)
                             (dissoc :time)
                             instrument)
        connected-instance (syn/connect synth-instance syn/destination)]
      (connected-instance audiocontext at duration)))

(re-frame/register-handler
  :initialize-db
  (fn  [_ _]
    db/default-db))
  
(re-frame/register-handler
  :playNote
  (fn [db [_, note]]
    (let [notes (:notes db)
          context (:audiocontext db)]
      (playNote! context note)
      ;(.log js/console note)
      (merge db {:notes (conj notes note)}))))

(re-frame/register-handler
  :start
  (fn [db [_, notes]]
    (let [context (audio-context)
          timeouts (:timeouts db)]
      (doseq [{:keys [time duration instrument isExtreme?] :as note} notes]
        (let [timeout (js/setTimeout 
                        #(re-frame/dispatch [:playNote note])
                        (* time 1000))]
          (conj! timeouts timeout)))
      (merge db {:playing? true 
                 :audiocontext context
                 :notes [] 
                 :timeouts timeouts}))))

(re-frame/register-handler
  :stop
  (fn [db _]
    (let [context (:audiocontext db)
          timeouts (:timeouts db)]
      (doseq [t (persistent! timeouts)]
        (js/clearTimeout t))
      (js/setTimeout #(.close context))
      (merge db {:playing? false :timeouts (transient [])}))))
    
(re-frame/register-handler
  :updateSpeed
  (fn [db [_, speed]]
    (merge db {:speed speed})))
      
(re-frame/register-handler
  :updateNotes
  (fn [db [_, notes]]
    (merge db {:notes notes})))