(ns sequences.core
    (:require [reagent.dom :refer [render]]
              [re-frame.core :as re-frame]
	            [devtools.core :as devtools]
              [sequences.handlers]
              [sequences.subs]
              [sequences.views :as views]))

(defn mount-root []
  (render [views/main] (.getElementById js/document "app")))

(defn ^:export init [] 
  (re-frame/dispatch-sync [:initialize-db])
	(devtools/set-pref! :install-sanity-hints true)
	(devtools/install!)
  (mount-root))