# slacker

**"Superman is a slacker."**

slacker is a simple RPC framework for Clojure based on
[aleph](https://github.com/ztellman/aleph) and
[carbonite](https://github.com/sunng87/carbonite/). I forked carbonite
because slacker requires it to work on clojure 1.2.

slacker is growing.

## Usage

### Leiningen

    :dependencies [[info.sunng/slacker "0.1.0-SNAPSHOT"]]

### Getting Started

Slacker will expose all your public functions under a given
namespace. 

``` clojure
(ns slapi)
(defn timestamp []
  (System/currentTimeMillis))

;; ...more functions
```             

To expose `slapi`, use:

``` clojure
(use 'slacker.server)
(start-slacker-server (the-ns 'slapi) 2104)
```

On the client side, define a facade for the remote function:

``` clojure
(use 'slacker.client)
(def sc (slackerc "localhost" 2104)
(defremote sc timestamp)
(timestamp)
```

### Options in defremote

You are specify the remote function name when the name is occupied in
current namespace

``` clojure
(defremote sc remote-time
  :remote-name "timestamp")
```

If you add an `:async` flag to `defremote`, then the facade will be
asynchronous which returns a *promise* when you call it. You should
deref it by yourself to get the return value.

``` clojure
(defremote timestamp :async true)
@(timestamp)
```

You can also assign a callback for an async facade.

``` clojure
(defremote timestamp :callback #(println %))
(timestamp)
```

### Serialize additional types

By default, most clojure data types are registered in carbonite. (As
kryo requires you to **register** a class before you can serialize
it.) To add your own types, you should register your custom
serializers on *both server side and client side*.

``` clojure
(use '[slacker common server])
(register-serializers some-serializers)
(start-slacker-server ...)
```
[Carbonite](https://github.com/revelytix/carbonite "carbonite") has some docs on how to create your own serializers.

## License

Copyright (C) 2011 Sun Ning

Distributed under the Eclipse Public License, the same as Clojure.
