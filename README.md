# Clearman

## Synopsis

Clearman is a Clojure library for creating
[Gearman](http://gearman.org) Clients and Workers. It uses
[Aleph](http://github.com/ztellman/aleph) to communicate with the
Gearman server.

There is currently a first release on clojars:
```clojure
;; in your project.clj ...
[clearman "0.0.1"]
```

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

### Client

Not to be outdone by the worker implementation, Clearman clients are a
little different than normal as well (as well as somewhat half baked
so far, you've been warned).

A Clearman "client" is just a single task queue connected to a single
Gearman server to run a single Gearman function. All three of the
singles in that sentence may change, but for now that was the path of
least resistance to getting something working in
asynchronous-land. You create a task queue pointed at a server with
your various callbacks, add tasks to it, and run them. That's about it so far.

It's probably easier to show and explain, so see below.

## Usage

### Workers

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

### Clients

```clojure
;; client

(use 'clearman.client)

;; create some functions to handle various events
(defn on-created
      [job]
      (println "he fell for it: " (:handle job))) ;; get the job handle

(defn on-complete
      [job]	
      (println "my job here is done: " (:data job))) ;; see the finished data

;; this will run the "reverse" function on the Gearman serever
;; at localhost:4730 (you'll see how below) and fire the registered
;; callbacks when those things happen
(create-task-queue "reverse" "localhost:4730" :on-created on-created
		   	     		      :on-complete on-complete)


;; if you want to create seperate queues for the same function, you can 
;; use the :function argument. you'll refer to this queue by "a-queue"
;; later on but it will run the reverse function. for now, this is the 
;; "right way" if you are using multiple servers
(create-task-queue "a-queue" "localhost:4730" :function "reverse"
                                              :on-created on-created
		                              :on-complete on-complete)


;; now add some tasks to run. you can optionally specify a priority
;; and a unique id
(add-tasks "a-queue" "this is the job data")
(add-tasks "a-queue" "this is more job data" :priority :high :unique-id "123")

;; at this point, these tasks are queued but haven't run. to run them:

(run-tasks "a-queue")

;; you can add more tasks and keep running them forever

;; when you are done with the queue, destroy it to close the connection and
;; clean things up. destroying a queue will also destroy any un-run tasks.
(destroy-task-queue "a-queue")

;; and thats it for now
```

## Status

Clearman is pretty new and probably not totally stable. I've been
focusing mainly on the worker part of the library since that is my
main need currently, so that interface probably won't change much
now. If you find bugs or have suggestions, please let me know.

## TODO

 * Implement multi-server task queues
 * Write tests/examples
 * Lots of cleanup and shuffling
 * A server implementation too?
 * Admin client interface

## License

Copyright (C) 2012 Josh Rotenberg

Distributed under the Eclipse Public License, the same as Clojure.
