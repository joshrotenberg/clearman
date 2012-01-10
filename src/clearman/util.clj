(ns clearman.util)

(defn dump-packet
  [g]
  (apply str (map char (mapcat #(.array %) g))))