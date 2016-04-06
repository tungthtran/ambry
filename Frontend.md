Introduction

In order to support larger objects and achieve a higher throughput, Ambry is making a concerted effort towards making the whole stack (client, frontend, routing and backend) non-blocking. This document describes the design of the non-blocking front end, the REST framework behind it, the interaction with the routing library and the use of Netty as the NIO framework. 

The blocking paradigm experiences some problems currently:

    Inability to support larger objects - this is the single biggest reason Ambry is making this effort.
    Client has to make a call and wait for the operation to finish before the thread is released - this is wasteful.
    Blocking paradigms don't play well with some frameworks (like play).
    High memory pressure at our current front ends if we start supporting larger objects.

High Level Design

The non-blocking front end can be split into 3 well defined components:- 

    Remote Service layer - Responsible for interacting with any service that could potentially make network calls or do heavy processing (e.g. Router library) to perform the requested operations.
    Scaling layer - Acts as a conduit for all data that goes in and out of the front end. Responsible for enforcing the non-blocking paradigm and providing scaling independent of all other components.
    NIO layer - Performs all network related operations including encoding/decoding HTTP.

Although these components interact very closely, they are very modular in the sense that they have clear boundaries and provide specific services. The components are all started by a RestServer that also enforces the start order required for interaction dependencies.

![](image/image2015-11-19 16_32_53.png)