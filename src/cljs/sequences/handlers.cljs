(ns sequences.handlers
    (:require [re-frame.core :as re-frame]
              [sequences.synthesis :as syn]
              [leipzig.temperament :as temperament]
              [leipzig.melody :as melody]
              [sequences.db :as db]))

;;¯\_(ツ)_/¯
(defn audio-context
  "Construct an audio context in a way that works even if it's prefixed."
  []
  (if js/window.AudioContext. ; Some browsers e.g. Safari don't use the unprefixed version yet.
    (js/window.AudioContext.)
    (js/window.webkitAudioContext.)))

(defn play! [audiocontext notes]
  (doseq [{:keys [time duration instrument] :as note} notes]
    (let [at (+ time (.-currentTime audiocontext))
          synth-instance (-> note
                             (update :pitch temperament/equal)
                             (dissoc :time)
                             instrument)
          connected-instance (syn/connect synth-instance syn/destination)]
      (connected-instance audiocontext at duration))))

(re-frame/register-handler
  :initialize-db
  (fn  [_ _]
    db/default-db))

(re-frame/register-handler
  :start
  (fn [db [_, notes]]
    (let [context (audio-context)]
      (play! context notes)
      (merge db {:playing? true :sync (Date.now) :audiocontext context}))))

(re-frame/register-handler
  :stop
  (fn [db _]
    (let [context (:audiocontext db)]
      (.close context)
      (merge db {:playing? false :sync nil}))))