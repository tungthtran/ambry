###Introduction

In order to support larger objects and achieve a higher throughput, Ambry is making a concerted effort towards making the whole stack (client, frontend, routing and backend) non-blocking. This document describes the design of the non-blocking front end, the REST framework behind it, the interaction with the routing library and the use of Netty as the NIO framework. 

The blocking paradigm experiences some problems:

  1. Inability to support larger objects - this is the single biggest reason Ambry is making this effort.
  1. Client has to make a call and wait for the operation to finish before the thread is released - this is wasteful.
  1. Blocking paradigms don't play well with some frameworks (like play).
  1. High memory pressure at our current front ends if we start supporting larger objects.

###High Level Design

The non-blocking front end can be split into 3 well defined components:- 

  1. Remote Service layer - Responsible for interacting with any service that could potentially make network calls or do heavy processing (e.g. Router library) to perform the requested operations.
  1. Scaling layer - Acts as a conduit for all data that goes in and out of the front end. Responsible for enforcing the non-blocking paradigm and providing scaling independent of all other components.
  1. NIO layer - Performs all network related operations including encoding/decoding HTTP.

Although these components interact very closely, they are very modular in the sense that they have clear boundaries and provide specific services. The components are all started by a RestServer that also enforces the start order required for interaction dependencies.

[[images/image2015-11-19 16_32_53.png]]

####Component Description

The following sections describe the parts that make up each of these components along with a brief explanation of where they fit in.

**Interaction enablers**

In order to better understand the different layers and the rationale behind their design, it is useful to understand the tools that the layers can use to exchange data and control. Interaction enablers are interfaces that enable the different components to interact with each other in a way that is agnostic to the underlying implementations of each of the components. These interfaces are implemented by the components that generate/consume the data that needs to be shared.

_ReadableStreamChannel_

This is an interface that enables interaction between the front end and the library that contacts the remote services (like the Router library). Through this interface, data can be streamed (in the form of bytes) between the interacting pieces as if reading it through a channel (usually they are actually reading from an underlying network channel). An implementation of this interface is required through the RestRequest interface at the NIO layer. When a blob is being POSTed, the remote service library will "pull" data from the front end through a ReadableStreamChannel. If the remote service library needs to return response bodies, it will need to provide an implementation of this interface that can be used with the RestResponseHandler. ReadableStreamChannel is designed for asynchronous reads and focuses on avoiding copies and supporting back pressure naturally.

_RestRequest_

This interface extends the ReadableStreamChannel interface and is implemented by the NIO layer. It enables interaction between all the components of the front end in a way that is agnostic to the NIO layer framework. In addition to helping the remote service library pull data from the client (through the front end), it enables the scaling and remote service service layers to process the request correctly.

_RestResponseChannel_

This interface, implemented by the NIO layer, provides a way for the remote service and scaling layers to return processed responses to the client. The APIs it provides deal with bytes only and thus it is agnostic to the kind of the data being returned. It is the responsibility of NIO layer to encode the data into HTTP and send it over the network to the client. 

####Remote Service Layer

This layer mainly interacts with the remote service library by calling the right APIs but is also responsible for doing any pre processing (like ID transformations, anti virus checks etc) before making those calls. One instance of a single RemoteService is started by the RestServer. 

In Ambry, this layer is usually singleton and stateless i.e it does not maintain state or context about the requests flowing through it. It is also responsible for pre-processing responses since responses arrive as callbacks from the Router library. Pre-processing usually involves setting response headers - the actual bytes are streamed out in the RestResponseHandler.

####Scaling Layer

This layer is the core of the non-blocking front end. It enforces the non-blocking paradigm and acts as a conduit for data flowing between the remote service layer and the NIO layer. The framework consists of: -

  _RestRequestHandler_ - This is the component that handles requests submitted by the NIO layer and hands them off to the remote service layer. Internally, it can maintain a number of scaling units that can be scaled independently of all other components. The number of scaling units has a direct impact on throughput and latency.

  _RestResponseHandler_ - This is the component that handles responses submitted by the remote service layer and streams the bytes to the network via the NIO layer. Internally, it can maintain a number of scaling units that can be scaled independently of all other components. The number of scaling units has a direct impact on throughput and latency.

_AsyncRequestResponseHandler_

AsyncRequestResponseHandler is an implementation of both RestRequestHandler and RestResponseHandler. It processes both requests and responses asynchronously. Requests are handled using one or more scaling units called AsyncRequestWorker. Due to the asynchronous nature of ReadableStreamChannel, response handling does not need scaling units. In order to process requests and responses, each scaling unit maintains some state :-

    Requests that are waiting to be processed (Request queue)- This is a queue of requests that are awaiting processing. Requests are enqueued by the NIO layer and dequeued and processed using the remote service layer.
    Responses waiting to be sent out (Response set) - This is a list of responses that are ready to be streamed to the client. The responses are represented by a ReadableStreamChannel and will be sent over the provided RestResponseChannel. If an exception was provided, an appropriate error message is constructed and returned to the client. 

The scaling units are CPU bound and perform all the CPU bound tasks.

####NIO Layer

The NIO layer is responsible for all network related operations including encoding/decoding HTTP. On the receiving side, the NIO framework is expected to provide a way to listen on a certain port for requests from clients, accept them, decode the HTTP data received and handoff this data to the scaling framework in a NIO framework agnostic format (RestRequest and RestResponseChannel). On the sending side, the NIO layer is expected to provide an implementation of RestResponseChannel to return processed responses back to the client.

The NIO layer also needs to maintain some state. For the layer as a whole, it needs to maintain the instance of RestRequestHandler that can be used for all channels and all requests. In addition, each channel might have to maintain some per request state :-

  1. The RestRequest that it is currently processing (required state per request) - This is required per request if content is expected since content will have to be added to the RestRequest.
  1. The RestResponseChannel (required state per request) - This has to be maintained per request since the RestResponseChannel has to be informed of any errors during NIO layer processing. 

####Component Interaction

The following sections describe how components interact with each other to execute operations.
Operation Execution

Much of this section uses Ambry and its Router library as a means of presenting the design. It should be easy to draw parallels and design any RemoteService that might need to be implemented. Some parts of the design and functionality of AsyncRequestResponseHandler are also presented and assumed to be in use.

_Common operations_

* Receiving requests

When a request is received, the NIO layer first packages its own representation of a HTTP request into a implementation of RestRequest (that the NIO layer provides). It passes this RestRequest along with a RestResponseChannel (that can be used to return a response to the request) to the RestRequestHandler. The request is then enqueued to be handled asynchronously at the RestRequestHandler.

* Receiving content

In GET, DELETE and HEAD requests, no valid content is expected. In a POST request, we expect content with the request. Any content received is added by the NIO layer to the RestRequest. Since the implementation of RestRequest is provided by the NIO layer, this can be done internally without involving the scaling layer. This content should be available for reading (at the remote service library) through the read operations of ReadableStreamChannel. Exceptions are thrown in case valid state transitions are not respected.

* Dequeing requests inside the AsyncRequestResponseHandler

Every request submitted to the AsyncRequestResponseHandler is handed off to a AsyncRequestWorker. The AsyncRequestWorker has a thread that regularly dequeues RestRequests from the request queue in order to process them. The handling of a dequeued request depends on the type of request.

**GET**

* Handling dequeued requests at the Remote Service (AmbryBlobStorageService)

For handleGet, AmbryBlobStorageService extracts the blob ID (and sub-resource) from the request , interacts with any required external services and does pre processing of request data if required (All this will be non blocking). 

For a GET request, we require both blob properties (to update headers) and the content of the blob. To this end, we create a Callback object for a getBlobInfo call first. This Callback object contains a function that needs to be called on operation completion and also encapsulates all the details required to make a subsequent getBlob call. The getBlobInfo method of the Router is then called with the blob ID and Callback.

`public interface Callback<T> {`
     `public void onCompletion(T result, Exception exception);`
`}`

* On getBlobInfo callback received

When the getBlobInfo callback is received, the response headers are populated. The Callback invokes the getBlob method of the Router with the blob ID and a new Callback that encapsulates all the information required to send a response.

`public class HeadForGetCallback<BlobInfo> {`
    `private final RestResponseHandler restResponseHandler;`
    `private final RestResponseChannel restResponseChannel;`
    `private final RestRequest restRequest; `
    `private final Router router;`
 
    `public HeadForGetCallback(RestResponseHandler restResponseHandler, RestResponseChannel restResponseChannel, RestRequest restRequest,`
        `Router router) {`
      `this.restResponseHandler = restRequestResponseHandler;`
      `this.restResponseChannel = restResponseChannel;`
      `this.restRequest = restRequest;`
      `this.router = router;`
    `}`
    `public void onCompletion(BlobInfo result, Exception exception) {`
      `if (exception == null) {`
        `// update headers in RestResponseChannel.`
        `// get blob id from RestRequest.`
        `// create GetCallback.`
        `router.getBlob(blobId, getCallback);`
      `} else {`
        `restResponseHandler.handleResponse(restRequest, restResponseChannel, null, exception); `
      `}`
    `}`
`}`

* Router

At the Router, a future that will eventually contain the result of any operations invoked is created and returned immediately to AmbryBlobStorageService. This ensures that the thread of the AsyncRequestWorker is not blocked. For getBlobInfo, the result is a BlobInfo object and for getBlob, the result is a ReadableStreamChannel representing blob data.  

The getBlobInfo callback is invoked with a BlobInfo when both the blob properties and user metadata are available. The getBlob callback is invoked with a ReadableStreamChannel representing blob data when at least one byte of the blob is available. In both cases, if there was an exception while executing the request, the Router invokes the callback with the exception that caused the request to fail.

* On getBlob callback received

When the getBlob callback is received, any necessary headers are updated and the response is submitted to the RestResponseHandler (AsyncRequestResponseHandler). The ReadableStreamChannel - RestResponseChannel pair is added to the response set and the response reading is initiated (which is asynchronous because of the design of ReadableStreamChannel). Once the response reading is complete (which is known via the callback), all remaining state can be cleaned up.

`public class GetCallback<ReadableStreamChannel> {`
    `private final RestResponseHandler restResponseHandler;`
    `private final RestResponseChannel restResponseChannel;`
    `private final RestRequest restRequest; `
  
    `public GetCallback(RestResponseHandler restResponseHandler, RestResponseChannel restResponseChannel, RestRequest restRequest) {`
      `this.restResponseHandler = restRequestResponseHandler;`
      `this.restResponseChannel = restResponseChannel;`
      `this.restRequest = restRequest;`
    `}`
 
    `public void onCompletion(ReadableStreamChannel result, Exception exception) {`
      `// update headers if required.`
      `restResponseHandler.handleResponse(restRequest, restResponseChannel, result, exception);`
    `}`
`}`