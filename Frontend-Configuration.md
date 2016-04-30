# Important frontend configurations

## Frontend configs

  CDN Cache validity in seconds for non-private blobs for GET.  
  @Config("frontend.cache.validity.seconds")  
  @Default("365*24*60*60")  
  
  The IdConverterFactory that needs to be used by AmbryBlobStorageService to convert IDs.  
  @Config("frontend.id.converter.factory")  
  @Default("com.github.ambry.frontend.AmbryIdConverterFactory")  

  The SecurityServiceFactory that needs to be used by AmbryBlobStorageService to validate requests.  
  @Config("frontend.security.service.factory")  
  @Default("com.github.ambry.frontend.AmbryIdConverterFactory")  

## RestServer configs

  The BlobStorageServiceFactory that needs to be used by the RestServer for bootstrapping the BlobStorageService.  
  @Config("rest.server.blob.storage.service.factory")  
  
  The NioServerFactory that needs to be used by the RestServer for bootstrapping the NioServer  
  @Config("rest.server.nio.server.factory")  
  @Default("com.github.ambry.rest.NettyServerFactory")  
  
  The number of scaling units in RestRequestHandler that will handle requests.  
  @Config("rest.server.request.handler.scaling.unit.count")  
  @Default("5")  
  
  The RestRequestHandlerFactory that needs to be used by the RestServer for bootstrapping the RestRequestHandler  
  @Config("rest.server.request.handler.factory")  
  @Default("com.github.ambry.rest.AsyncRequestResponseHandlerFactory")  
  
  The number of scaling units in RestResponseHandler handle responses.  
  @Config("rest.server.response.handler.scaling.unit.count")  
  @Default("5")  
  
  The RestResponseHandlerFactory that needs to be used by the RestServer for bootstrapping the RestResponseHandler.  
  @Config("rest.server.response.handler.factory")  
  @Default("com.github.ambry.rest.AsyncRequestResponseHandlerFactory")  

  The RouterFactory that needs to be used by the RestServer for bootstrapping the Router.  
  @Config("rest.server.router.factory")  
  @Default("com.github.ambry.router.CoordinatorBackedRouterFactory")  
 
  Request Headers that needs to be logged as part of public access log entries  
  @Config("rest.server.public.access.log.request.headers")  
  @Default(
      "Host,Referer,User-Agent,Content-Length,x-ambry-content-type,x-ambry-owner-id,x-ambry-ttl,x-ambry-private,x-ambry-service-id,X-Forwarded-For")  
  
  Response Headers that needs to be logged as part of public access log entries  
  @Config("rest.server.public.access.log.response.headers")  
  @Default("Location,x-ambry-blob-size")  
  
  Health check URI for load balancers (VIPs)  
  @Config("rest.server.health.check.uri")  
  @Default("/healthCheck")  

## Netty configs

  Number of netty boss threads.  
  @Config("netty.server.boss.thread.count")  
  @Default("1")  

  The amount of time a channel is allowed to be idle before it's closed. 0 to disable.  
  @Config("netty.server.idle.time.seconds")  
  @Default("60")  
  
  Port on which to run netty server.  
  @Config("netty.server.port")  
  @Default("1174")  
  
  Socket backlog size. Defines the number of connections that can wait in queue to be accepted.  
  @Config("netty.server.so.backlog")  
  @Default("100")  
  
  Number of netty worker threads.  
  @Config("netty.server.worker.thread.count")  
  @Default("1")  
  
## Coordinator configs

  The hostname of the node upon which the coordinator runs.  
  @Config("coordinator.hostname")  

  The name of the datacenter in which the coordinator is located.  
  @Config("coordinator.datacenter.name")  

  The number of threads in the requester thread pool.  
  @Config("coordinator.requester.pool.size")  
  @Default("100")  

  Timeout for operations that the coordinator issues.  
  @Config("coordinator.operation.timeout.ms")  
  @Default("2000")  

  The factory class the coordinator uses to create a connection pool.  
  @Config("coordinator.connection.pool.factory")  
  @Default("com.github.ambry.network.BlockingChannelConnectionPoolFactory")  

  Timeout for checking out a connection from the connection pool  
  @Config("coordinator.connection.pool.checkout.timeout.ms")  
  @Default("1000")  

  Indicates if all operations should or should not do cross dc proxy calls  
  @Config("coordinator.cross.dc.proxy.call.enable")  
  @Default("true")  

## ConnectionPool configs

  The read buffer size in bytes for a connection.  
  @Config("connectionpool.read.buffer.size.bytes")  
  @Default("1048576")  
  
  The write buffer size in bytes for a connection.  
  @Config("connectionpool.write.buffer.size.bytes")  
  @Default("1048576")  
  
  Read timeout in milliseconds for a connection.  
  @Config("connectionpool.read.timeout.ms")  
  @Default("1500")  
  
  Connect timeout in milliseconds for a connection.  
  @Config("connectionpool.connect.timeout.ms")  
  @Default("800")  
  
  The max connections allowed per host per port for plain text  
  @Config("connectionpool.max.connections.per.port.plain.text")  
  @Default("5")  
  
  The max connections allowed per host per port for ssl  
  @Config("connectionpool.max.connections.per.port.ssl")  
  @Default("2")  
  
## Clustermap configs

  The factory class used to get the resource state policies.  
  @Config("clustermap.resourcestatepolicy.factory")  
  @Default("com.github.ambry.clustermap.FixedBackoffResourceStatePolicyFactory")  
  
  The fixed timeout based resource state handling checks if we have had a 'threshold' number of consecutive errors, and if  
  so, considers the resource as down for 'retry backoff' milliseconds.  

  The threshold for the number of consecutive errors to tolerate for a datanode.  
  @Config("clustermap.fixedtimeout.datanode.error.threshold")  
  @Default("6")  
  
  The time to wait before a datanode is retried after it has been determined to be down.  
  @Config("clustermap.fixedtimeout.datanode.retry.backoff.ms")  
  @Default("5 * 60 * 1000")  
  
  The threshold for the number of errors to tolerate for a disk.  
  @Config("clustermap.fixedtimeout.disk.error.threshold")  
  @Default("1")  
  
  The time to wait before a disk is retried after it has been determined to be down.  
  @Config("clustermap.fixedtimeout.disk.retry.backoff.ms")  
  @Default("10 * 60 * 1000")  