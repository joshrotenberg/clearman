(ns clearman.protocol
  (:use gloss.core ))

;; \0REQ
(def REQ (String. (byte-array [(byte 0x00)
                               (byte 0x52)
                               (byte 0x45)
                               (byte 0x51)])))

;; \0RES
(def RES (String. (byte-array [(byte 0x00)
                               (byte 0x52)
                               (byte 0x45)
                               (byte 0x53)])))

(defcodec request-type
  (enum :int32 {:can-do 1
                :cant-do 2
                :reset-abilities 3
                :pre-sleep 4
                :submit-job 7
                :grab-job 9
                :work-status 12
                :work-complete 13
                :work-fail 14
                :get-status 15
                :echo-req 16
                :submit-job-bg 18
                :submit-job-high 21
                :set-client-id 22
                :can-do-timeout 23
                :all-yours 24
                :work-exception 25
                :option-req 26
                :work-data 28
                :work-warning 29
                :grab-job-uniq 30
                :submit-job-high-bg 32
                :submit-job-low 33
                :submit-job-low-bg 34
                :submit-job-sched 35
                :submit-job-epoch 36
                }))

(defcodec response-type
  (enum :int32 {:noop 6
                :job-created 8
                :no-job 10
                :job-assign 11
                :work-status 12
                :work-complete 13
                :work-fail 14
                :echo-res 17
                :error 19
                :status-res 20
                :work-exception 25
                :option-res 27
                :work-data 28
                :job-assign-uniq 31
                }))

(defcodec data-string (compile-frame
                       (string :utf-8 :prefix :int32)
                       (fn [x] (->> x (interpose "\0") (apply str)))
                       (fn [x] (seq (.split ^String x "\0")))))

(def gearman-request (ordered-map
                      :req (string :utf-8 :length  4)
                      :type request-type
                      :data data-string))

(def gearman-response (ordered-map
                       :res (string :utf-8 :length  4)
                       :type response-type
                       :data data-string))
