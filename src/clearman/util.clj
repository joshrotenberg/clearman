(ns clearman.util
  (:use clearman.protocol))

(defn client-arg-map
  [hostport]
  (let [[h p] (.split (name hostport) ":")
        p (if-not (nil? p)  (read-string p) 4730)
        server-key (keyword hostport)]
    {:host h
     :port p
     :encoder gearman-request
     :decoder gearman-response}))

(defn dump-packet
  [g]
  (apply str (map char (mapcat #(.array %) g))))