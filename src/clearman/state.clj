(ns clearman.state)

(defonce worker-functions (atom {})) ;; current functions
(defonce server-channels (ref {})) ;; current servers
(defonce running-tasks (atom {})) ;; running tasks
