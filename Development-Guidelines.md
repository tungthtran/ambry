# Ambry specific guidelines
 
## Basic Stuff

  * Avoid cryptic abbreviations. Single letter variable names are fine in very short methods with few variables, otherwise make them informative. 
  * Clear code is preferable to comments. When possible make your naming so good you don't need comments. When that isn't possible comments should be thought of as mandatory, write them to be read.
  *  Logging, configuration, and public APIs are our "UI". Make them pretty, consistent, and usable.
  *  Don't be sloppy. Don't check in commented out code: we use version control, it is still there in the history. Don't leave TODOs in the code or FIXMEs if you can help it. Don't leave println statements in the code. Hopefully this is all obvious.
  *  We want people to use our stuff, which means we need clear, correct documentation. User documentation should be considered a part of any user-facing the feature, just like unit tests or performance results.
  *  For any new component that is built, check if it is something that would be useful to be pluggable. If so, define appropriate APIs in the api package.
  *  Designing APIs should be taken very seriously. The APIs should be absolutely necessary, fit into the design and should be easily understood by reading. Avoid polluting the API to just ease the implementation.

## Logging

  *  We use SLF4J (with Log4J) for Java.
  *  Logging is one third of our "UI" and it should be taken seriously. Please take the time to assess the logs when making a change to ensure that the important things are getting logged and there is no junk there.
  *  Don't include a stack trace in INFO, or above, unless there is really something wrong. Stack traces in logs should signal something is wrong, not be informative. If you want to be informative, write an actual log line that say's what's important, and save the stack trace for DEBUG.
  *  Logging statements should be complete sentences with proper capitalization that are written to be read by a person not necessarily familiar with the source code. 
  * It is fine to put in hacky little logging statements when debugging, but either clean them up or remove them before checking in.
  *  Logging should not mention class names or internal variables.
  *  There are six levels of logging TRACE, DEBUG, INFO, WARN, ERROR, and FATAL, they should be used as follows.
     1. INFO is the level you should assume the software will be run in. INFO messages are things which are not bad but which the user will definitely want to know about every time they occur.
     2. TRACE and DEBUG are both things you turn on when something is wrong and you want to figure out what is going on. DEBUG should not be so fine grained that it will seriously effect the performance. TRACE can be anything.
     3. WARN and ERROR indicate something that is bad. Use WARN if you aren't totally sure it is bad, and ERROR if you are.
        Use FATAL only right before calling System.exit().

## Unit Tests

  *  New patches should come with unit tests that verify the functionality being added.
  *  Unit tests are first rate code, and should be treated like it. They should not contain code duplication, cryptic hackery, or anything like that.
  *  Unit tests should test the least amount of code possible
  *  Do not use sleep or other timing assumptions in tests, it is always, always, always wrong and will fail intermittently on any test server with other things going on that causes delays. Write tests in such a way that they are not timing dependent. One thing that will help this is to never directly use the system clock in code (i.e. System.currentTimeMillis) but instead to use getTime: () => Long, so that time can be mocked.
  *  It must be possible to run the tests in parallel, without having them collide. This is a practical thing to allow multiple branches to CI on a single CI server. This means you can't hard code directories or ports or things like that in tests because two instances will step on each other.

## Configuration

  *  Configuration is the final third of our "UI".
  *  All configuration names that define time must end with .ms (e.g. foo.bar.ms=1000).
  *  All configuration names that define a byte size must end with .bytes (e.g. foo.bar.bytes=1000).
  *  All configuration names that define a factory class must end with .factory.class (e.g. store.factory.class).
  *  Configuration will always be defined as simple key/value pairs (e.g. a=b).
  *  When configuration is related, it must be grouped using the same prefix (e.g. replication.token.factory="StoreToken", replication.num.replica.threads=1).
  *  Names should be thought through from the point of view of the person using the config, but often programmers choose configuration names that make sense for someone reading the code.
  *  Often the value that makes most sense in configuration is not the one most useful to program with. For example, let's say you want to throttle I/O to avoid using up all the I/O bandwidth. The easiest thing to implement is to give a "sleep time" configuration that let's the program sleep after doing I/O to throttle down its rate. But notice how hard it is to correctly use this configuration parameter, the user has to figure out the rate of I/O on the machine, and then do a bunch of arithmetic to calculate the right sleep time to give the desired rate of I/O on the system. It is much, much, much better to just have the user configure the maximum I/O rate they want to allow (say 5MB/sec) and then calculate the appropriate sleep time from that and the actual I/O rate. Another way to say this is that configuration should always be in terms of the quantity that the user knows, not the quantity you want to use.
  *  Configuration is the answer to problems we can't solve up front for some reason--if there is a way to just choose a best value do that instead.

## Concurrency

  *  Encapsulate synchronization. That is, locks should be private member variables within a class and only one class or method should need to be examined to verify the correctness of the synchronization strategy.
  *  There are a number of gotchas with threads and threadpools: is the daemon flag set appropriately for your threads? are your threads being named in a way that will distinguish their purpose in a thread dump?
  *  Prefer the java.util.concurrent packages to either low-level wait-notify, custom locking/synchronization, or higher level scala-specific primitives. The util.concurrent stuff is well thought out and actually works correctly. There is a generally feeling that threads and locking are not going to be the concurrency primitives of the future because of a variety of well-known weaknesses they have. This is probably true, but they have the advantage of actually being mature enough to use for high-performance software right now; their well-known deficiencies are easily worked around by equally well known best-practices.

## Backwards Compatibility

  *  Ambry uses Semantic Versioning.
  *  Backwards incompatible API changes, config changes, or library upgrades should only happen between major revision changes, or when the major revision is 0.
  *  ambry-utils should not depend on any other ambry package. 
     ambry-api should not depend on any other ambry package other than ambry-utils. 
     ambry-network should not depend on any other packages other than ambry-api and ambry-utils. 
     ambry-coordinator should not depend on any of the server side packages. It can depend on ambry-shared, ambry-utils, ambry-messageformat, ambry-api and ambry-clustermap. 
     ambry-store is at the lowest level and should depend on ambry-api, ambry-clustermap, ambry-metrics and ambry-utils.
