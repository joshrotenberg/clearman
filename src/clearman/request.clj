(ns clearman.request
  (:use clearman.protocol
        lamina.core))

(declare send-work-complete)
;; request senders
(defn send-request
  [ch type & args]
  (enqueue @(ch) {:req REQ
                  :type type
                  :data args}))

(defn send-can-do
  [ch fname]
  (send-request ch :can-do fname))

(defn send-can-do-timeout
  [ch fname timeout]
  (send-request ch :can-do fname timeout))

(defn send-cant-do
  [ch fname]
  (send-request ch :cant-do fname))

(defn send-reset-abilities
  [ch]
  (send-request ch :reset-abilities))

(defn send-pre-sleep
  [ch]
  (send-request ch :pre-sleep))

(defn send-grab-job
  [ch]
  (send-request ch :grab-job))

(defn send-grab-job-uniq
  [ch]
  (send-request ch :grab-job-uniq))

(defn send-work-data
  [ch handle data]
  (send-request ch :work-data handle data))

(defn send-work-warning
  [ch handle data]
  (send-request ch :work-warning handle data))

(defn send-work-status
  [ch handle numerator denominator]
  (send-request ch :work-status handle numerator denominator))

(defn send-work-complete
  [ch handle data]
  (send-request ch :work-complete handle data))

(defn send-work-fail
  [ch handle]
  (send-request ch :work-fail handle))

(defn send-set-client-id
  [ch id]
  (send-request ch :set-client-id id))

(defn send-echo-req
  [ch data]
  (send-request ch :echo-req data))

(defn send-all-yours
  [ch]
  (send-request ch :all-yours))
