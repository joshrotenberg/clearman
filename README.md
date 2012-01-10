# Clearman

## Synopsis

Clearman is a Clojure library for creating
[Gearman](http://gearman.org) Clients and Workers. It uses
[Aleph](http://github.com/ztellman/aleph) to communicate with the
Gearman server.

### Client

Starting implementation now.

### Worker

For the most part, Clearman works a lot like Gearman Worker
implementations in other languages: specify one or more Gearman
servers, specify one or more functions, and run. There are a few
differences, though, in how Clearman runs workers. Where other
implementations usually run a single process and sequentially process
jobs from their configured Gearman servers, Clearman runs each
configured server independently in a thread. Because of this, the
concept of a "Worker" in Clearman is better thought of as a single
connection to a Gearman server and the 1 or more functions it knows
how to process, rather than the collection of all server connections
and functions. In addition, instead of offering a single entry point
(usually via a work() function/method), Clearman internally handles
the loop and lets you just say "start working". You can add/remove
servers and functions at any time. If you add a server connection
while others are running, you can safely just `(start-workers)` again
to start any new connections.

Clearman uses Aleph/Lamina/Gloss for the network, channel, and
protocol levels, respectively.

## Usage

```clojure
;; workers 

(use 'clearman.worker)

;; add and connect to a Gearman server
(add-server "localhost:4730") 

;; add ye olde reverse function
(add-function "reverse" #(apply str (reverse (:data %))))

;; loop forever and reverse all the strings
(start-workers)

;; until forever ends, of course
(stop-workers)

;; you can add and delete multiple servers and/or functions, of course
(add-server "some.other.host") ;; defaults to port 4730

;; start that worker now. note that in all functions, host:port names
;; can be strings or keywords, so "localhost:4730" and :localhost:4730
;; are equivalent.
(start-worker :some.other.host)

;; see who is running
(worker-status) ;; returns a map; {:some.other.host :running
                ;;                 :localhost:4730 :stopped}
```

## Status

clearman is pretty new and probably not totally stable. I've been
focusing mainly on the worker part of the library since that is my
main need currently, so that interface probably won't change much
now. If you find bugs or have suggestions, please let me know.

## License

Copyright (C) 2012 Josh Rotenberg

Distributed under the Eclipse Public License, the same as Clojure.
