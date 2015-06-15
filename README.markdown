Circulure is a re-implementation of Circular.io's backend using Clojure.

This code is for instructional purposes only -- I kind of whipped it up,
so there might be some issues running it on a server (for example, it will
happily serve up all the files in resources/Circular). That said, if
you want to run it on your local machine, it should be totally safe.

## Dependencies

* You'll need MongoDB running.
* You'll need to set the environment variables TWITTER_CONSUMER_KEY
  and TWITTER_CONSUMER_SECRET to your credentials.
* You'll need boot to run this code.

## Running as dev

```
$ boot repl
```

Then head to core.clj and run the `run-jetty` form in the comment
at the bottom.


## Packaging and running it as a jar

```
$ boot package
$ java -jar target/
```

Then you can `$ java -jar target/circulure-0.1.0-STANDALONE.jar` to start
the server. You still need mongodb running on localhost though.

