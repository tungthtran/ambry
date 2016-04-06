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

**Operation Flow**  
The basic flow for all operations is as follows:
The frontend submits an operation to the OperationController. The OperationController will create a Future and in turn submit all the information to the appropriate manager (PutManager for puts, GetManager for gets and DeleteManager for deletes), all of which will add this Operation to their respective internal list of operations. It will then return this future back to the caller.
These operations get executed in the context of the RequestResponseHandler thread. This thread does the following in a loop:

    while (true) {
      List<NetworkSend> requests = requestor.poll();
      selector.poll(timeout, requests);
      requestor.onResponse(selector.connected(), selector.disconnected(), 
      selector.completedSends(), selector.completedReceives());
    }

The crux of the logic for each operation, therefore gets executed in the context of requestor’s poll and onResponse() methods as we’ll see in the next sections.

The Requestor (which is the OperationController) does the following within the poll() and onResponse() methods:

    List<NetworkSend> OperationManager.poll() 
    {
      // these are ids that were successfully put for an operation that eventually failed:
      List<String> idsToDelete = putManager.getIdsToDelete();
  
      // this is a best effort to delete ids for cleanup purposes (these may fail and we will
      // not do anything about it at this time).
      deleteManager.submit(idsToDelete);

      return new ArrayList<NetworkSend>().addAll(putManager.poll(), getManager.poll(),deleteManager.poll());
    }

    // the poll for the respective managers simply iterates over the operations to give them
    // a chance to execute their business logic and get more requests to return.
    List<NetworkSend> {put,get,delete}Manager.poll()
    {
      List<NetworkSend> requests;
      for each op in {put,get,delete}Operations:
        requests.addAll(op.fetchRequests());
      return requests;
    }

Similarly, the onResponse() call of the OperationManager does the following:

    OperationManager.onResponse(List<String> connected, List<String> disconnected, List<NetworkSend> completedSends, List<NetworkReceive> completedReceives)
    {
      for each s in connected:
        connectionManager.checkIn(s);
      for each s in disconnected:
        connectionManager.updateDisconnection(s);
      // update operations in a clean way - benefit being the operation can immediately get
      // ready for the subsequent action (more requests, slip puts, consider the operation as
      // failed, etc.)

      // on receiving a response, call into the respective operation manager -> operation to
      // handle the response
      for each recv in completedReceives:
        connectionManager.checkIn(connection(recv));
      operationManager.getOperationManagerFromRecv(recv).onResponse(recv);
    }

In the next sections, we will talk about how for each kind of operation, the poll() and onResponse() methods are handled as that is the crux of the operation specific logic.

_Put Operation_  
For every Put, the PutManager will create a PutOperation object. The PutOperation maintains all the metadata associated with the operation. Please see the Appendix for the PutOperation class.

The crux of the operation logic runs in the context of the RequestResponseHandler thread as discussed above. In addition, Put Manager will have a ChunkFiller thread that is responsible for asynchronously reading from the ReadableStreamChannel associated with the operation (created and submitted by the frontend/client) and filling in chunks.

A PutOperation will have a list of PutChunks that will keep track of a chunk and its state. The number of PutChunks for an operation determines the “pipeline factor” and will be chosen empirically (we plan to start out with 4). Note that the PutMetadataChunk is also a PutChunk with additional functionalities. PutChunk will have states associated with them and we'll see in the next section on how these states are used. A PutChunk is responsible for reading in some part of the blob from the channel to form a chunk, and completing the put of that chunk. The methods of this object will be called by the ChunkFiller as well as the RequestResponseHandler (indirectly via the). The Appendix details the details of these classes.

The flow of the Put Operation is as follows.

FrontEnd thread
Makes the putBlob() call and provides a ReadableStreamChannel, blob properties and user metadata, and the callback to be called on completion.
The PutManager creates the Future, and the PutOperation object which is added to its list, and returns the Future.

ChunkFiller
The ChunkFillerThread within the PutManager will do the following in a loop:

    
    for each putOperation:
      while (!channel_is_completely_read && there_are_free_or_building_chunks) {
      // get the putChunk to fill - this would be the chunk in Building state if
      // there is one, else one of the Free ones in the putChunk array.
      PutChunk putChunk = next_putChunk();
      // this will fill in the chunk, allocating from the bufferpool as required.
      // Chunk will go from Free->Building->Ready as it gets read.
      putChunk.fillFrom(channel);
      if (!putChunk.isReady()) {
        // this means the channel doesn't have any more data to be read at this time.
        break;
      }
    }

RequestResponseHandler

The general flow of the RequestResponseHandler is covered earlier. Here let us look at exactly how the poll() and onResponse() calls are handled for puts in the context of this thread:

    poll(): for each putOperation, do fetchRequests():

    List<NetworkSend> putOperation.fetchRequests()
    {
      List<NetworkSend> requests = new List<NetworkSend>();
      if (putMetadataChunk == null) {
      for each putChunk:
        if (putChunk.isReady()) {
          // fetch more requests. This will internally handle timeouts, quorums,
          // slip puts and return list of requests accordingly. When requests are 
          // created, the putChunks will internally checkout connections from the 
          // connection manager, if there are any. If no connection is available, then 
          // the connection manager will return null (after initiating new connections 
          // if required). Only if a connection is successfully checked out will a 
          // request be created and returned by the putChunk.
          requests.addAll(putChunk.fetchRequests());
        }
      } else {
        if (putMetadataChunk.isReady()) {
          requests.addAll(putMetadataChunk.fetchRequests());
        }
      }
    }

onResponse():

void putOperation.onResponse(NetworkReceive response)
{
  if (successful_response) {
    PutChunk putChunk = getAssociatedChunk(response.correlation_id);
    if (putChunk == null) {
      // we must have removed the association because another response
      // marked the chunk complete.
      return;
    }
    // these names are just placeholders, but the idea is
    // that this method updates the putChunk's state.
    putChunk.updateState(response);
    if (putChunk.isComplete()) { // complete if succeeded or failed
      if (putChunk.isSuccess()) {
        // the chunk has been successfully put across enough nodes
        if (isSimpleBlob || putChunk == putMetdataChunk) {
          returnedFuture.set(blobId);
          call callback;
          cleanup();
          return;
        } else {
          putMetadataChunk.update(putChunk.getBlobId(),
                                  putChunk.getPosition());
        }
      } else {
        cleanup()
        mark future as failed with appropriate exception.
        call callback.
        return;
      }
    }
  }
}


Get Operation

FrontEnd Thread
The frontend thread will submit the operation, which the OperationController will submit to the GetManager which will create the GetOperation objects. The appropriate future object will be created and returned based on whether the operation is a getBlob() or getBlobInfo().
 
RequestResponseHandler 
The steps followed here will be exactly the same as with puts as far as this thread is concerned. The only difference is in the implementation of the Requestor.poll() and the Requestor.onResponse() methods.

The poll() within GetManager is as follows. For each GetOperation, do fetchRequests():

List<NetworkSend> getOperation.fetchRequests()
{
  List<NetworkSend> requests = new List<NetworkSend>();
  handle_error_and_timeouts();
  requests = create_more_get_requests();
  return requests;
}

onResponse():

void getOperation.onResponse(NetworkReceive response)
{
  // a response could be discarded for various reasons - for example, 
  // if another response was received for a peer request.
  if (discardable_response) {
    return;
  }
  if (error_response) {
    handle_error_and_timeouts();
    return;
  }
  if (type == GetBlobInfo) {
    blobInfo = new BlobInfo(response.receivedBytes);
    returnedFutre.set(blobInfo);
    call callback;
  } else {
    // A getChunk simply wraps over the received buffer
    getChunk = new GetChunk(response.receivedBytes);
    deserialize();
    if (is_first_chunk) {
      channel = new chunks_based_readable_stream_channel(...);
      returnedFuture.set(channel);
      call callback;
      if (data_blob) { // simple blob
        channel.update(chunk, pos);
      } else {
        fill in getMetadataChunk structure;
      }
    } else { // if not first chunk
      channel.update(chunk, pos);
    }
    if (all_chunks_received) {
      cleanup();
      // chunks themselves will be freed by the channel as and when they are read out
      // and on close().
    }
  }


BufferPool Utilization
The selector will read in the complete response (including the whole chunk) into a BoundedByteBuffer which allocates memory within itself today. In order to ensure that the memory for chunks come in from the buffer pool of the router, and to avoid additional copies, the BoundedByteBuffer will be modified to take in a buffer pool from which buffers will be allocated to read in the responses. As the allocation request should always succeed when done from the BoundedByteBuffer, any “lmiting” logic will have to be handled outside of the selector, within the poll() operations. The operation managers will avoid creating more requests if the buffer pool has reached its threshold. Any more requests will be created only in the next iteration (as responses are received and consumed and buffer pool utilization goes below the threshold).
Delete Operation

RequestResponseHandler

poll():

List<NetworkSend> deleteOperation.fetchRequests()
{
  List<NetworkSend> requests = new List<NetworkSend>();
  handle_error_and_timeouts();
  requests = create_more_delete_requests();
  return requests;
}

onResponse():

void deleteOperation.onResponse(NetworkReceive recv)
{
  if (enough_responses_have_been_received) {
    returnedFuture.set();
    call callback;
    return;
  } else {
    update state;
  }
}
