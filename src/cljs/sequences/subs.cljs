(ns sequences.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]))

(re-frame/register-sub
 	:audio-context
 	(fn [db]
   	(reaction (:audio-context @db))))

(re-frame/register-sub
  :playing?
 	(fn [db]
   	(reaction (:playing? @db))))

(re-frame/register-sub
  :notes
 	(fn [db]
   	(reaction (:notes @db))))

(re-frame/register-sub
  :sync
 	(fn [db]
   	(reaction (:sync @db))))
  
(re-frame/register-sub
  :division
 	(fn [db]
   	(reaction (:division @db))))
  
(re-frame/register-sub
  :tempo
 	(fn [db]
   	(reaction (:tempo @db))))