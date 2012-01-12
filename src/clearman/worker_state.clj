(ns clearman.worker-state)

(defonce worker-functions (atom {})) 
(defonce worker-channels (ref {}))
