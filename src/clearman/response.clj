(ns clearman.response
  (:use clearman.worker-state
        clearman.request
        clearman.protocol
        lamina.core))

(defmulti handle-response (fn [ch response] (:type response)))

(defmethod handle-response :echo-res [ch response] 
  (let [{:keys [res type size data]} response]
    (prn "echo response: " data)))

(defmethod handle-response :default [ch response]
  (let [{:keys [res type size data]} response]
      (prn "unknown response: " response)))

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
    (prn unique-id)
    (enqueue ch {:req REQ
                 :type :work-complete
                 :data [handle ((:fn fn-info) {:data payload
                                               :handle handle
                                               :channel ch})]})))

;; response handlers
(defmethod handle-response :error [ch response] 
  (let [{:keys [res type data]} response
        [code text] data]
      (println "error: " code text)))

(defmethod handle-response :noop [ch response] 
  (let [{:keys [res type data]} response])
  (println "noop"))

(defmethod handle-response :no-job [ch response] 
  (let [{:keys [res type data]} response])
  (println "no-job"))

