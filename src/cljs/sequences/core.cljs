(ns sequences.core
    (:require [reagent.dom :refer [render]]
              [reagent.session :as session]
              [re-frame.core :as re-frame]
	            [devtools.core :as devtools]
              [sequences.handlers]
              [sequences.subs]
              [sequences.views :as views]
              [sequences.newtonian.core :as newtonian]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [goog.history.EventType :as HistoryEventType])
  (:import goog.History))

(defn page []
  [(session/get :current-page)])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page views/infinity))

(secretary/defroute "/newton" []
  (session/put! :current-page newtonian/main))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
        (events/listen
          HistoryEventType/NAVIGATE
          (fn [event]
              (secretary/dispatch! (.-token event))))
        (.setEnabled true)))

;; -------------------------
;; Initialize app

(defn mount-root []
  (render [#'page] (.getElementById js/document "app")))

(defn ^:export init [] 
  (re-frame/dispatch-sync [:initialize-db])
  (hook-browser-navigation!)
	(devtools/set-pref! :install-sanity-hints true)
	(devtools/install!)
  (mount-root))