### Introduction
In a typical setup, Ambry consists of a frontend tier and a data tier. The frontend is stateless and provides a REST interface to the client, and uses the routing library (the “coordinator” today) to route requests to the appropriate servers. That being said, Ambry also supports embedding the routing library directly within client. Any design decision we have made must support both models.

The frontend, apart from providing a REST interface to the clients, also provides support for interacting with external services. Within LinkedIn, this layer performs antivirus checks, creates and provides the notification system (kafka) to use for notifications about blob creations and deletions, and communicates with other databases.

The responsibilities of the routing library are primarily the following:
* Choose partitions for a PUT operation.
* Determine the data nodes to be contacted for a particular operation and the order and parallelism for the same, determine the number of responses that must be received in order to consider that operation a success, and determine how to do retries in case of failures.
* Interact with the cluster manager to pick nodes and partitions that are active, and inform the cluster manager about failed nodes and partitions.
* Chunk objects if they are too large during PUTs and distribute them individually.

### Problem Statement
Today, the frontend and the router are blocking. This means that an operation holds on to the request thread until the entire blob is sent or received to/from a data node. This severely impacts the throughput and latency and makes it infeasible to support large blobs (which is the main reason for not supporting large blobs today). The absence of any chunking also means that for large blobs, the partition usage can be uneven and inefficient, besides affecting the throughput.

In order to fix this problem, we need to make both the frontend layer and the routing layer non-blocking. This document describes the non-blocking router design. The non-blocking frontend is going to be written in a way that allows for using Netty or Rest.li in a non-blocking way. A detailed design document for the same can be found here: 

### Proposed Design
**Concepts and Terminology**
_Chunking_
The new router implements chunking of large objects. The router is configured with a MAX_CHUNK_SIZE that will be honored during a put operation. Any object that is greater than this size is treated as a large object and split into multiple chunk blobs and one metadata blob, each of which will be distributed among the partitions independently. The chunk blobs will be of size MAX_CHUNK_SIZE except, likely, the last. The metadata blob will store the blob ids of all the chunk blobs of the large object (in order). This blob will also be stored like the other blobs. The id of the indirect blob is treated as the id of the object and is what gets returned to the caller.

Small objects is supported by composing it as a single simple blob.
Chunking will only be known to the router. The frontend layer will always deal with the whole object. The server will treat simple blobs, part blobs and indirect blobs in exactly the same way.

_Slip Puts_
The router performs slip puts of blobs. A partition is first chosen for a blob and a blob id is generated. Then, the router sends put requests to the nodes associated with that partition (based on some configured policy). If enough number of successful responses are not received to consider that operation as a success, the new router will choose a different partition, generate a different blob id and re-attempt the operation internally, rather than fail it.

Slip puts are particularly important with large objects as a single chunk failure essentially fails the put for the entire object.

_Operations vs. Requests_
An Operation represents the whole operation from the caller’s perspective - this is what the Frontend - Router interactions deal with.

A Request represents a particular request that is sent to a datanode. This is what the Router - DataNode interactions deal with. A single operation could and will lead to multiple requests.
 
_API_
The Router interface is designed to support streaming and non-blocking capabilities. The API consists of a simple, narrow and intuitive set of methods. These methods essentially “submit” the operation and return a Future without blocking. Optionally, these methods take Callbacks that will be called on operation completion. The Router API is given in the index.

_Scaling Units_
The router will consist of a set of scaling units. The number of scaling units will be configurable and configured empirically based on the load and performance. A call that comes to the router will be internally assigned to one of the scaling units, which will handle the request and the response for that call. The scaling units work independently of each other. In the rest of this document, unless the context indicates otherwise, the term Router is used to refer to a scaling unit.

**Components and Flow**
In this section, we will discuss the various classes and threads that make the router.
Classes
(Unless otherwise mentioned, all classes are concrete and under the ambry.router package)
* **OperationController**: The Router will consist of a list of scaling units in the form of OperationControllers, one of which will be picked for any given operation.
* **PutManager, GetManager, DeleteManager**: The OperationController will create and maintain the respective operation managers that will handle each type of operation.
* **PutOperation, GetOperation, DeleteOperation**: For every operation, the OperationController will call into the appropriate managers to create operation specific objects.
* **PutChunk**: A PutOperation will, among other things, contain a fixed number of PutChunks. Each PutChunk will be used to read in a data chunk from the ReadableStreamChannel, choose a partition, create requests and handle responses to put the chunk across the required number of datanodes, and then repeat the process by reading in a different chunk. It will also handle slip puts internally.
* **PutMetadataChunk**: Similar to a PutChunk (which it will extend) to manage the put of the metadata blob, if any. This will additionally consist of methods to update the data chunk id, and serialize the list of data chunk ids using the MessageFormat helpers.
* **GetChunk**: Similar to PutChunks, this class will hold chunks during a get blob operations.
* **ConnectionManager (ambry.network)**: this class will keep track of the current connections to datanodes, and will provide methods to checkout, and checkin connections back. This class will also ensure the max_connections_to_host is not exceeded.
* **RequestResponseHandler thread**: handles sending and receiving NetworkSend and NetworkReceives from the OperationController via a Selector. This will simply be a thread within the OperationController.
* **BufferPool (interface, ambry.utils)**: This will manage the memory allotted for the OperationController for buffering chunks across all operations. The Bufferpool will be common across the scaling units. The pool will support allocate() and deallocate() methods that will be used to allocate from and submit back to it. We will start with a simple implementation of a BufferPool that will simply do Bytebuffer.allocate().

