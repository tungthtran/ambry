Multi-tenancy is an essential requirement in any distributed system, and when multi-tenancy kicks in, security is more or less a must have requirement which any data system is expected to have. Also, security is of particular importance in today's world where cyber-attacks are a common occurrence and the threat of data breaches is a reality for businesses of all sizes, and at all levels from individual users to whole government entities. Hence we started of with adding minimal security features to Ambry. 

Key features that are added to Ambry from a Security standpoint are:
1. Any interaction between frontend and servers and between servers(for replication purposes) should be encryptable if need be. We use SSL/TLS encryption for this purpose.
2. Configs should be available to define which interactions should be enabled. By default no encryption should be enabled.
3. Separate ports should be available on ambry-servers to speak plain text and SSL.

###SSL/TLS

Among the cryptographic protocols used for authentication and encryption, TLS/SSL is most commonly used mechanism. TLS and SSL are cryptographic protocols designed to provide communications security over a computer network.  They use X.509 certificates and hence asymmetric cryptography to authenticate the counterparty with whom they are communicating, and to negotiate a symmetric session key. This session key is then used to encrypt data flowing between the parties.

We are not getting into details as to how actual SSL works as its pretty well known algorithm. So, lets get into the details of adding SSL to Ambry.

###Components required for SSL

1. Key stores: Used to store credentials. It stores private keys and certificates corresponding to its own public key which the server sends it to the client during authentication.

2. Trust stores: Used to verify credentials. It stores public keys and certificates from 3rd party(CA) which is used to authenticate the server/client.

3. JSSE library APIs

**Config parameters**

Enlisted here are all the configs required for adding SSL to ambry:
certificate path, keystore path, keystore password truststore path, truststore password, keystore provider, keystore type, SSLEnabledDataCenters, cipher suites, SSL version, enable two way authentication

SSLEnabledDataCenters is the list of colos to which the connection should be encrypted from current colo. Each datacenter should over-ride this param value as per its requirement. For instance, if there are 3 datacenters A, B and C and lets say any data to and from a different Datacenter should be encrypted, here is how this config will look like. Datacenter A will set this value to "B,C". Datacenter B will set it as "A,C" and datacenter C will set this value as "A,B".

**JSSE**
We plan to use JSSE APIs as part of Java SDK for getting SSL to Ambry. Before we get into further details into design, lets take a brief over view about SSLEngine which forms the main crux of communication between client and server.

**SSLEngine**
It encapsulates an SSL/TLS state machine and operates on inbound and outbound byte buffers supplied by the user of the SSLEngine. The following diagram illustrates the flow of data from the application, to the SSLEngine, to the transport mechanism, and back.


The application, shown on the left, supplies application (plaintext) data in an application buffer and passes it to the SSLEngine. The SSLEngine processes the data contained in the buffer, or any handshaking data, to produce SSL/TLS encoded data and places it the network buffer supplied by the application. The application is then responsible for using an appropriate transport (shown on the right) to send the contents of the network buffer to its peer. Upon receiving SSL/TLS encoded data from its peer (via the transport), the application places the data into a network buffer and passes it to SSLEngine. The SSLEngine processes the network buffer's contents to produce handshaking data or application data.

A port dedicated to SSL connections obviates the need for any Ambry-specific protocol signalling that authentication is beginning or negotiating an authentication mechanism (since this is all implicit in the fact that the client is connecting on that port). Clients simply begin the session by sending the standard SSL CLIENT-HELLO message. This has two advantages:
  the SSL handshake provides message integrity
  clients can use standard SSL libraries to establish the connection

And do remember, that this SSL encryption happens only for interactions between ambry-server to ambry-server during replication and router to ambry-server interactions during request processing for now. Customer authentication support for SSL can also be made possible by adding a same logic for interactions between client and frontend. But for now, we are enabling encryption only between frontends and servers, and among different servers.

##Simple Client Server interaction using SSLEngine

To summarize the steps required for a client to communicate with a server using SSLEngine is as follows:
  1. Initialize KeyManager and TrustManager.
  2. Initialize SSLContext using KeyManagers and trustmanagers. SSLContext is responsible for holding all configs like cihper suites, key sizes, provider type of key store and so on.  
  3. Create SSLEngine using SSLContext to the server. SSLEngine can be created only when remote data node address is known. (Hence, on the server side, we can create SSLEngine only on Accepting connections) One SSLEngine is created per client-server channel.
  4. Create the socketChannel to the server incase of client.
  5. Once we have the SSLEngine and socketChannel created, we go ahead with handshaking process. Handshaking involved few steps(to and fro), but leaving the details for explanation purpose.
  6. Once handshaking is done to negotiate the ciphers and the secure key, secure communication can happen.
     6 a. Wrap the data using SSLEngine before sending it through socketChannel
     6 b. On receiving response, unwrap the data using SSLEngine before processing the response.
  7. Shutdown the engine once done with communication.
Workflow

##Lets see how the entire control/data flow happens with SSL in Ambry.

_Set up and configs_

  1. Make sure we have all the config as mentioned above

  2. New ports have to be reserved for this purpose(SSL) on all ambry-servers.
 
Once these are set up on the server, rest of the flow is described below:

_Server_

  a. On Initializing a Server, SSLContext needs to be created which required KeyManager and TrustManager which in turn needs keystore path, keystore password, truststore path and truststore password and so on. Once SSLContext is initialized, ambry-server just waits for new connection request on this SSL port.

  b. On receiving new connections, SSLEngine needs to be created. On creating SSLEngine, we proceed onto handshaking. (We need peer host and peer port to create SSLEngine and hence we need to create one SSLEngine object per client connection)

  c. Until handshaking completes, no other read or write will happen and this handshaking happens in a non-blocking manner.

  d. On completing the handshake, Server sets the interest bits to read and waits to receive the actual encrypted request from the client.

  e. Usual read() and write() happens through engine which will wrap or unwrap when required after checking the status of handshake using nio selector pattern. After this, its going to be same as how a normal server will process a request.
  f. Once response is available, its sent to the SSLEngine which will encrypt the same before sending it over the network.

_Client_

  a. On Initializing a Client, SSLContext needs to be created similar to a Server which requires KeyManager and TrustManager which in turn needs keystore path, keystore password, truststore path and truststore password and so on. Once SSLContext is initialized, we create SSLEngine and SSLChannel and send connect request to the server and wait for CONNECT.

  b. On CONNECT, we proceed onto handshaking to negotiate the supported ciphers, protocols and so on.

  c. Until handshaking completes, no other read or write will happen. This happens in a blocking manner. We need some efforts to make it non-blocking. Yet to investigate further.

  d. On completing the handshake, Client sets the interest bits to WRITE and proceed on as usual

  e. Usual read() and write() happens through engine which will wrap or unwrap when required after checking the status of handshake using nio selector pattern

###Major tasks involved in adding SSL to Ambry from an implementation perspective

  1. First step is to support multiple ports for our servers. 
    a. This might involve modifying the schema for HardwareLayout and the Datanode representation. We should be able to obtain default port for a host or SSL port and send requests to the same. 
    b. A seperate Acceptor needs to be introduced for listening to the new port added.  
  2. Set up all the required components like Keystores (JKS) and trust stores to store Certificates and private keys for all frontends and servers. 
  3. Add SSLBlockingChannel on similar lines to BlockingChannel to make requests using SSL. 
  4. Introduce Transmission interface and PlainTextTransmission and SSLTransmissions (implementations) to carry out respective network read/write for plain text or ssl using the same selector.

##Detailed Design

  2. Adding BlockingSSLChannel is straight forward using SSLEngine. Just following the steps given above would suffice.

  3. Server is bit tricky as we have non blocking read and writes.  

**Current design with plain text**

We have a SocketServer class which is responsible for listening to a specific port for incoming requests. An Acceptor thread is created to accept incoming connections. On acceptance, it delegates the connection to a processor which takes the responsibility or handling the incoming request. A set of processors are created during initialization of SocketServer and passed to the acceptor and as stated in previous statement, its takes care of handling requests(read, write and close) on acceptance of a connection.

**Design with SSL**

We have two approaches that we could take

  1. Having two different SocketServers(SocketServer and SSLSocketServer). SocketServer remains the same as is and will be listening to default port w/o encryption. SSLSocketServer will be listening to SSL port and will have a separate acceptor and a separate list of processors.

PRO: Clear code, separate processors (Processor, SSLProcessor), selectors (Selector, SSLSelector).

CON: Static resource allocation (the number of Processors and SSLProcessors). The QPS from the secure port and non-secure port may change over time. Statically allocate the processor resource may lead to overload in one port while underload on the other port.

  2. Having a single SocketServer which has two acceptors(one listening to default port and another one listening to SSL port) and same set of processors will be shared among these two acceptors.

PRO: Share the same set of processors. Requests from secure port and non-secure port share the same queue in each processor and are served in FIFO manner.

CON: Selector in each processor needs to handle read, write and connect for requests from both secure port and non-secure port. The code in read, write and connect functions in Selector needs to use if-else to separate the operations.

Due to stated reasons, and other reasons as follows, we are going ahead with the 2nd approach:

  1. We can't expect applications to control the size of processor pool (SSL vs Non SSL) and emperically changing both)

  2. There could be starvation and under utilization of resources if we have different pool of resources for each type of port.

So, lets dive deeper into the second approach.

SocketServer initialization remains the same as current SocketServer, as most of the logic lies in Selector. SocketServer will initialize the SSLFactory depending on if it needs to listen to ssl port or not. It will spawn up two Acceptors (plain text and ssl) and same set of processors will be shared among both acceptors. On accepting connection, each acceptor will delegate it to one Processor. Processor starts off with registering the connection with the selector and begins handshaking.

Within register() in Selector we create a transmission object (either plain text or ssl) and attach it to the keys.

Transmission interface is what defines the protocol for driving the interactions with the connection/channel. read(), write(), and close() are the methods in Transmission used for our interaction purposes and will be implemented by plain text transmission and ssl transmission. From now on, whenever a key is chosen by the selector for read or write, we just need to call transmission.read() or tranmsisison.write() and the implementation will carry out the required task. SSLTransmission is what we are interested in here. It has all the components required for ssl, like SSLEngine, app read buffer, network read buffer, network write buffer, handshake status and so on. If you need more details do check it out here.
 