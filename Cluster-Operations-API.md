This document describes APIs that are available for viewing (and eventually managing) the Ambry cluster.
***
### GET peers
#### Description
This API can be used to obtain the peers of a given storage node (S) in the cluster. A peer is defined as a storage node that shares at least one partition with S. The response does not include S.
#### API
    GET /peers?name=<hostname>&port=<hostport>

| Query Parameter | Type | Required? | Description |
| --- | --- | --- | --- |
| name | String | Yes | The hostname of S as it is in the hardware layout |
| port | Integer | Yes | The plaintext port of S as it is in the hardware layout |

#### Returns
The peers of S as JSON. The JSON contains a single key ("peers") whose value is JSON array of peers. Each peer in the JSON array contains two pieces of information, "name" and "port", that describe the peer.
##### _Success response_
A successful response is indicated by the status code `200 OK`. The response body is the JSON that contains the peers of S.
##### _Failure response_
See [[standard error codes|Rest-API#standard-error-codes]].
#### Sample Response
    HTTP/1.1 200 OK
    Content-Type: application/json
    Content-Length: 131
    
    {"peers":[{"port":6667,"name":"host1.domain.com"},{"port":6667,"name":"host2.domain.com"},{"port":6667,"name":"host3.domain.com"}]}