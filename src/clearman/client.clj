(ns clearman.client
  (:use clearman.core
        clearman.protocol
        clearman.util
        aleph.tcp
        gloss.core
        lamina.core
        lamina.connections))

(defonce task-queues (ref {}))

(declare new-client)
(declare run-tasks)

(defn- dispatch-callback
  "Given a response type and the task-queue name, find the callback, if any
  and execute it with the returned data."
  [name response]
  (let [{:keys [type data]} response
        task-queue (name @task-queues)
        func (type task-queue)]
    (when-not (nil? func)
      (func data))))

(defn create-task-queue
  "Create a task queue for the given function. Accepts the queue name,
  a Gearman server to connect to (host or host:port as a string or keyword)
  and some or all of the following options/callbacks:

  :function - by default, the name provided will match the Gearman function
              to be executed. If :function is provided, however, name
              can be something else. This is useful if you want to have
              a seperate queue for the same function.

  :on-created, :on-complete, :on-fail, :on-retry, :on-status, :on-data
  :on-warning, :on-exception - provide a function for any/all of these
                               and it will be called when the server
                               sends back the appropriate response.
  "
  [name server & {:keys [function on-created on-complete
                         on-fail on-retry on-status
                         on-data on-warning on-exception]}]
  (dosync
   (let [client ((keyword server) (new-client server))]
     (receive-all @(client) #(dispatch-callback (keyword name) %))
     (alter task-queues assoc (keyword name)
            {:client client
             :function (keyword (or function name))
             :job-created on-created
             :work-complete on-complete
             :work-data on-data
             :work-status on-status
             :work-warning on-warning
             :work-exception on-exception
             :work-fail on-fail
             :channel (channel)}))))

(defn destroy-task-queue
  "Destroys a task queue if it exists, disconnecting from the Gearman server."
  [queue-name & args]
  (let [queue-name (keyword queue-name)
        task-queue (queue-name @task-queues)]
    (when-not (nil? task-queue)
      (dosync
       (close-connection (:client task-queue))
       (close (:channel task-queue))
       (alter task-queues dissoc queue-name)))))

(defn- job-priority
  [priority & background]
  (let [prio (vector priority (if (first background) true false))]
    (condp = prio
      [:high false] :submit-job-high
      [:high true] :submit-job-high-bg
      [:low false] :submit-job-low
      [:low true] :submit-job-low-bg
      [:normal true] :submit-job-bg
      :submit-job)))

(defn add-task
  "Enqueue a task in a task queue."
  [queue-name task & {:keys [priority unique-id background]}]
  (let [queue-name (keyword queue-name)
        task-queue (queue-name @task-queues)
        job-type (job-priority (or priority :normal) background)]
    (if (nil? task-queue)
      false
      (do
        (enqueue (:channel task-queue)
                 {:data task
                  :type job-type
                  :unique-id unique-id})))))

(defn run-tasks
  "Run any currently queued tasks in the specified task queue."
  [queue-name]
  (let [queue-name (keyword queue-name)
        task-queue (queue-name @task-queues)
        tasks (channel-seq (:channel task-queue))]
    (map #(enqueue @((:client task-queue))
                   {:req REQ
                    :type (:type %)
                    :data [(name (:function task-queue))
                           (:unique-id %)
                           (:data %)]}) tasks)))

(defn new-client
  "Create a Gearman client."
  [& servers]
  (if (zero? (count servers))
    nil
    (into {} (map (fn [s]
                    [(keyword s)
                     (persistent-connection
                      #(wait-for-result
                        (tcp-client (client-arg-map s)))) ]) servers))))






