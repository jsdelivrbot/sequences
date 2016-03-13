(ns sequences.db)
  
(def default-db
  { :audiocontext nil
  	:playing? false
  	:speed 1
  	:spin 0
  	:muted? false
  	:gain 0.04
  	:timeouts (transient [])
    :notes [] })
