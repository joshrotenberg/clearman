(ns clearman.worker
  (:use clearman.core
        clearman.worker-state
        clearman.request
        clearman.protocol
        clearman.util
        aleph.tcp
        gloss.core
        lamina.core
        lamina.connections
        all-the-while.core)
  (:use clojure.data)
  (:require [clojure.string :as str]))

;; response handlers

(defmulti handle-response (fn [ch response] (:type response)))

;; XXX it would be cool if the caller could add callbacks to handle these
;; after they do some default work
(defmethod handle-response :job-assign [ch response] 
  (let [{:keys [res type data]} response
        [handle function-name payload] data
        fn-info ((keyword function-name) @worker-functions)]
    (enqueue ch {:req REQ
                 :type :work-complete
                 :data [handle ((:fn fn-info) {:data payload
                                               :handle handle
                                               :channel ch})]})))

(defmethod handle-response :job-assign-uniq [ch response] 
  (let [{:keys [res type data]} response
        [handle function-name unique-id payload] data
        fn-info ((keyword function-name) @worker-functions)]
    (enqueue ch {:req REQ
                 :type :work-complete
                 :data [handle ((:fn fn-info) {:data payload
                                               :handle handle
                                               :channel ch})]})))


(defmethod handle-response :error [ch response] 
  (let [{:keys [res type data]} response
        [code text] data]))

(defmethod handle-response :noop [ch response] 
  (let [{:keys [res type data]} response]))

(defmethod handle-response :no-job [ch response] 
  (let [{:keys [res type data]} response]))

(defmethod handle-response :echo-res [ch response] 
  (let [{:keys [res type size data]} response]))

(defmethod handle-response :default [ch response]
  (let [{:keys [res type size data]} response]))


;; watchers 
(add-watch worker-channels :server-update
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
                       chs (map :ch (vals @worker-channels))]
                   (doseq [ch chs]
                     (send-cant-do ch (name fname)))))
               (when added
                 (let [fname (name (first (keys added)))
                       chs (map :ch (vals @worker-channels))]
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
(declare worker-task)
(defn add-server
  "Add and (re)connect to a Gearman server. If an entry for this server already
   exists, it will be disconnected, removed and re-added/reconnected. This
   avoids orphaned connections."
  [hostport & {:keys [timeout client-id] }]
  (when ((keyword hostport) @worker-channels)
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
     (let [ch (make-channel h p)]
       (alter worker-channels assoc server-key
              {:ch ch :timeout (or timeout 5000)})
       (create-task server-key (worker-task server-key))))))

(defn remove-server
  "Remove and disconnect from a Gearman server."
  [hostport]
  (let [hostport (keyword hostport)
        server (hostport @worker-channels)]
    (when server
      (dosync
       (close-connection (:ch server))
       (alter worker-channels dissoc hostport)
       (remove-task hostport)))))

;; functions
(defn add-function
  "Register a function. The function should accept a single argument which
  is a map containing the job handle, the server connection and the job data.
  Optionally specify a :timeout"
  [function-name function & {:keys [timeout]}]
  (swap! worker-functions assoc (keyword function-name)
         {:fn function :timeout (or timeout 0)})
  true)

(defn remove-function
  "Unregister a function."
  [function-name]
  (swap! worker-functions dissoc (keyword function-name)))

;; running
(defn worker-task
  "Returns a function that represents a single request response cycle with a
   Gearman server. Returns nil if the server isn't found."
  [server-name]
  (let [server-name (keyword server-name)
        [ch timeout] ((juxt :ch :timeout) (server-name @worker-channels))]
    (prn @worker-channels)
    (if (nil? ch)
      nil
    (fn []
      (send-grab-job-uniq ch) ;; grab a job
      ;; (send-grab-job ch)
      (handle-response @(ch) (wait-for-message @(ch))) ;; handle it (if any)
      
      (send-pre-sleep ch) ;; say we are going to sleep
      ;; at this point we are still waiting for a response from our pre
      ;; sleep request. if we get one, return immediately and start
      ;; this loop over, otherwise wait for the timeout and start over
      (try
        (handle-response @(ch) (wait-for-message @(ch) timeout))
        (catch Exception e))))))

(defn worker-tasks
  "Returns a worker-task for each of the currently configured servers."
  []
  (map #(worker-task %) (keys @worker-channels)))

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
  (doseq [s (keys @worker-channels)]
    (start-worker s)))

(defn stop-workers
  []
  (doseq [s (keys @worker-channels)]
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

