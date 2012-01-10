(ns clearman.worker
  (:use clearman.core
        clearman.state
        clearman.request
        clearman.response
        clearman.protocol
        aleph.tcp
        gloss.core
        lamina.core
        lamina.connections
        all-the-while.core)
  (:use clojure.data)
  (:require [clojure.string :as str]))

;; watchers 
(add-watch server-channels :server-update
           ;; when a server is added, we need to send it our current list
           ;; of capabilities with a can-do request
           (fn [context ref old new]
             (let [[removed added _] (diff old new)]
               (when added
                 (let [ch (:ch (first (vals added)))]
                   (doseq [fname (keys @worker-functions)]
                     (send-can-do ch (name fname))))))))

(add-watch worker-functions :function-update
           ;; when a function is added or removed, we need to let the
           ;; currenlty configured servers know with a can-do or cant-do
           (fn [context ref old new]
             (let [[removed added _] (diff old new)]
               (when removed
                 (let [fname (name (first (keys removed)))
                       chs (map :ch (vals @server-channels))]
                   (doseq [ch chs]
                     (send-cant-do ch (name fname)))))
               (when added
                 (let [fname (name (first (keys added)))
                       chs (map :ch (vals @server-channels))]
                   (doseq [ch chs]
                     (send-can-do ch (name fname))))))))

;; servers
(defn- make-channel
  "Creates a channel with a persistent connection to the specified host
   and port with our protocol encoders and decoders registered."
  [host port]
  (persistent-connection
   #(wait-for-result (tcp-client {:host host
                                  :port port
                                  :encoder gearman-request
                                  :decoder gearman-response}))))

(declare remove-server)
(defn add-server
  "Add and (re)connect to a Gearman server. If an entry for this server already
   exists, it will be disconnected, removed and re-added/reconnected. This
   avoids orphaned connections."
 [hostport & {:keys [timeout client-id] }]
 (when ((keyword hostport) @server-channels)
   (remove-server hostport))
 (let [[h p] (.split (name hostport) ":")
       p (if-not (nil? p)  (read-string p) 4730)
       server-key (keyword hostport)]
   ;; XXX: using this for async req/res handling works ok,
   ;; but since the protocol is pretty much always a one for one req/res
   ;; situation, its just as ok to use the synchronous wait-for-message
   ;; in the main worker task. possibly revisit, though
   ;; (receive-all @(ch) #(handle-response @(ch) %))
   (dosync
    (alter server-channels assoc server-key
           {:ch (make-channel h p) :timeout (or timeout 5000)})
    (create-task server-key (worker-task server-key)))))

(defn remove-server
  "Remove and disconnect from a Gearman server."
  [hostport]
  (let [hostport (keyword hostport)
        server (hostport @server-channels)]
    (when server
      (dosync
       (close-connection (:ch server))
       (alter server-channels dissoc hostport)
       (remove-task hostport)))))

;; functions
(defn add-function
  "Register a function. The function should accept a single argument which
  is a map containing the job handle, the server connection and the job data.
  Optionally specify a :timeout"
  [function-name function & {:keys [timeout]}]
  (swap! worker-functions assoc (keyword function-name)
         {:fn function :timeout (or timeout 0)}))

(defn remove-function
  "Unregister a function."
  [function-name]
  (swap! worker-functions dissoc (keyword function-name)))

;; running
(defn worker-task
  "Returns a function that represents a single request response cycle with a
   Gearman server. Returns nil if the server isn't found."
  [server-name]
  (let [[ch timeout] ((juxt :ch :timeout)
                      ((keyword server-name) @server-channels))]
    (if (nil? ch)
      nil
    (fn []
      (send-grab-job ch)
      (handle-response @(ch) (wait-for-message @(ch)))
      (send-pre-sleep ch)
      (try
        (handle-response @(ch) (wait-for-message @(ch) timeout))
        (catch Exception e))))))

(defn worker-tasks
  "Returns a worker-task for each of the currently configured servers."
  []
  (map #(worker-task %) (keys @server-channels)))

(defn start-worker
  "Given a server name, start the worker<->server communication. Returns
  true if the worker is started successfully."
  [server-name]
  (start-task server-name))

  ;; (let [task (worker-task server-name)]
  ;;   (if (nil? task)
  ;;     false
  ;;     (do

  ;;       (start-task server-name)
  ;;       true))))

(defn stop-worker
  "Given a server name, stop the worker<->server communication."
  [server-name]
  (stop-task server-name))
;;(remove-task server-name))

(defn start-workers
  []
  (doseq [s (keys @server-channels)]
    (start-worker s)))

(defn stop-workers
  []
  (doseq [s (keys @server-channels)]
    (stop-worker s)))

(defn worker-status
  []
  (task-status))


;; (defn start-worker
;;   "Given a server name, start the worker<->server communication."
;;   [server-name]
;;   (let [task (worker-task server-name)]
;;     (if (nil? task)
;;       false
;;       (swap! running-tasks assoc (keyword server-name) (every 10 task)))))

;; (defn stop-worker
;;   "Given a server name, stop the worker<->server communication."
;;   [server-name]
;;   (let [running ((keyword server-name) @running-tasks)]
;;     (when running
;;       (do
;;         (stop running)
;;         (swap! running-tasks dissoc (keyword server-name))))))

