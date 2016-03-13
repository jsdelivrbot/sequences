(ns sequences.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]
              [sequences.db :refer [default-db]]))
  
(defn react
  [key]
  (re-frame/register-sub 
     key 
     (fn [db] (reaction (key @db)))))
   
(doseq [k (keys default-db)] (react k))