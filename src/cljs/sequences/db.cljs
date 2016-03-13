(ns sequences.db)
  
(def default-db
  { :audiocontext nil
  	:playing? false
  	:speed 1
  	:timeouts (transient [])
    :notes [] })
