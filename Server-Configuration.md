## Important server configurations

### Store configs
  The frequency at which the data gets flushed to disk
   @Config("store.data.flush.interval.seconds")
   @Default("60")

  The max size of the index that can reside in memory in bytes for a single store
   @Config("store.index.max.memory.size.bytes")
   @Default("20971520")

  The max number of the elements in the index that can be in memory for a single store
  @Config("store.index.max.number.of.inmem.elements")
  @Default("10000")

  The max number of entries that the journal will return each time it is queried for entries
  @Config("store.max.number.of.entries.to.return.from.journal")
  @Default("5000")

  The max probability of a false positive for the index bloom filter
  @Config("store.index.bloom.max.false.positive.probability")
  @Default("0.01")

  How long (in days) a key must be in deleted state before it is hard deleted.
  @Config("store.deleted.message.retention.days")
  @Default("7")

  The rate of I/O allowed for hard deletes.
  @Config("store.hard.delete.bytes.per.sec")
  @Default("1*1024*1024")

  Whether hard deletes are to be enabled or not
  @Config("store.enable.hard.delete")
  @Default("false")

### SSLConfig

  The SSL protocol for SSLContext
  @Config("ssl.context.protocol")
  @Default("TLS")

  The enabled protocols for SSLEngine, a comma separated list of values
  @Config("ssl.enabled.protocols")
  @Default("TLSv1.2")

  The SSL key store type
  @Config("ssl.keystore.type")
  @Default("JKS")

  The SSL key store path
  @Config("ssl.keystore.path")
  @Default("")

  The SSL key store password
  There could be multiple keys in one key store
  This password is to protect the integrity of the entire key store
  @Config("ssl.keystore.password")
  @Default("")

  The SSL key password
  The key store protects each private key with its individual password
  @Config("ssl.key.password")
  @Default("")

  The SSL trust store type
  @Config("ssl.truststore.type")
  @Default("JKS")

  The SSL trust store path
  @Config("ssl.truststore.path")
  @Default("")

  The SSL trust store password
  @Config("ssl.truststore.password")
  @Default("")

  The SSL supported cipher suites, a comma separated list of values
  @Config("ssl.cipher.suites")
  @Default("")

  List of Datacenters to which local node needs SSL encryption to communicate
  @Config("ssl.enabled.datacenters")
  @Default("")

### Replication Config

  The number of replica threads on each server that runs the replication protocol for intra dc replication
  @Config("replication.no.of.intra.dc.replica.threads")
  @Default("1")

  The number of replica threads on each server that runs the replication protocol for inter dc replication
  @Config("replication.no.of.inter.dc.replica.threads")
  @Default("1")

  The timeout to get a connection checkout from the connection pool for replication
  @Config("replication.connection.pool.checkout.timeout.ms")
  @Default("5000")

  The flush interval for persisting the replica tokens to disk
  @Config("replication.token.flush.interval.seconds")
  @Default("300")

  The initial delay to start the replica token flush thread
  @Config("replication.token.flush.delay.seconds")
  @Default("5")

  The fetch size is an approximate total size that a remote server would return on a fetch request.
  This is not guaranteed to be always obeyed. For example, if a single blob is larger than the fetch size
  the entire blob would be returned
  @Config("replication.fetch.size.in.bytes")
  @Default("1048576")

  The time for which replication waits between replication of remote replicas of a partition
  @Config("replication.wait.time.between.replicas.ms")
  @Default("1000")

  The max lag above which replication does not wait between replicas. A larger value would slow down replication
  while reduces the chance of conflicts with direct puts. A smaller value would speed up replication but
  increase the chance of conflicts with direct puts
  @Config("replication.max.lag.for.wait.time.in.bytes")
  @Default("5242880")

  Whether message stream should be tested for validity so that only valid ones are considered during replication
  @Config("replication.validate.message.stream")
  @Default("false")

### Server Config
  The number of request handler threads used by the server to process requests
  @Config("server.request.handler.num.of.threads")
  @Default("7")

  The number of scheduler threads the server will use to perform background tasks (store, replication)
  @Config("server.scheduler.num.of.threads")
  @Default("10")

### Network Config

  The number of network threads that the server uses for handling network requests
  @Config("num.network.threads")
  @Default("3")

  The number of io threads that the server uses for carrying out network requests
  @Config("num.io.threads")
  @Default("8")

  The number of queued requests allowed before blocking the network threads
  @Config("queued.max.requests")
  @Default("500")

  The port to listen and accept connections on
  @Config("port")
  @Default("6667")

  Hostname of server. If this is set, it will only bind to this address. If this is not set,
  it will bind to all interfaces, and publish one to ZK
  @Config("host.name")
  @Default("localhost")

  The SO_SNDBUFF buffer of the socket sever sockets
  @Config("socket.send.buffer.bytes")
  @Default("1048576")

  The SO_RCVBUFF buffer of the socket sever sockets
  @Config("socket.receive.buffer.bytes")
  @Default("1048576")

  The maximum number of bytes in a socket request
  @Config("socket.request.max.bytes")
  @Default("104857600")