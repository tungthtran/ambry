At a high level, the Admin supports normal GET, DELETE and HEAD and some special operations through the same HTTP methods. This document describes all the APIs in detail.
***
### GET
#### Description
This API gets the content of the blob represented by the blob ID. When used with sub-resources, it gets user metadata (and optionally blob properties) instead of the actual content of the blob. It can also get the list of replicas that store the blob.
#### API
    GET /<ambry-id>/<sub-resource>
    Sub-resources: BlobInfo, UserMetadata, Replicas

| Parameter | Type | Required? | Description |
| --- | --- | --- | --- |
| ambry-id | String | Yes |The ID of the blob whose content is requested |
| sub-resource | String | No | One of the listed sub-resources |

| Request Header | Type | Required? | Description |
| --- | --- | --- | --- |
| x-ambry-get-option | String | No | See [[options|Admin-API#get-options]].|
#### Returns
###### Without sub-resources
The content of the blob.
###### UserMetadata
The user metadata as response headers.
###### BlobInfo
The user metadata and blob properties as response headers.
###### Replicas
The location of the all the replicas that have the blob including hostname, port and mount path
##### _Success response_
A successful response is indicated by the status code `200 OK`. 
###### Without sub-resources
The body of the response will contain the content of the blob.
###### UserMetadata
The response headers will contain the user metadata that was uploaded (if any).

| Response Header | Type | Description |
| --- | --- | --- |
| x-ambry-um- | String | Zero or more headers with this prefix that represent user metadata |
###### BlobInfo
The response headers will contain the user metadata that was uploaded (if any) and the properties of the blob.

| Response Header | Type | Description |
| --- | --- | --- |
| x-ambry-blob-size | Long | The size of the blob |
| x-ambry-service-id | String | The ID of the service that uploaded the blob |
| x-ambry-content-type | String | The type of content in the blob |
| x-ambry-private | Boolean | `true` if the blob is private. `false` if the blob is public |
| x-ambry-creation-time | Long | The time at which the blob was created |
| x-ambry-ttl (if supplied at upload)| Long | The time in seconds for which the blob is valid from its creation time |
| x-ambry-owner-id (if supplied at upload) | String | The owner of the blob. |
| x-ambry-um- (if supplied at upload) | String | Zero or more headers with this prefix that represent user metadata |
###### Replicas
The body of the response will contain a JSON listing all the replicas that contain the blob

##### _Failure response_
See [[standard error codes|Admin-API#standard-error-codes]].  
#### Sample Response
###### Without sub-resources
    HTTP/1.1 200 OK
    Date: Sun, 01 May 2016 05:36:41 GMT
    Last-Modified: Sun, 01 May 2016 05:35:21 GMT
    x-ambry-blob-size: 2000
    Content-Type: image/gif
    Expires: Mon, 01 May 2017 05:36:41 GMT
    Cache-Control: max-age=31536000
    Transfer-Encoding: chunked

    <file-content>
###### BlobInfo
    HTTP/1.1 200 OK
    Date: Sun, 01 May 2016 05:38:47 GMT
    Last-Modified: Sun, 01 May 2016 05:35:21 GMT
    x-ambry-blob-size: 2000
    x-ambry-service-id: API-Demo
    x-ambry-creation-time: Sun, 01 May 2016 05:35:21 GMT
    x-ambry-private: false
    x-ambry-content-type: image/gif
    x-ambry-owner-id: demo-user
    x-ambry-um-description: Demonstration Image
    Content-Length: 0
###### UserMetadata
    HTTP/1.1 200 OK
    Date: Sun, 01 May 2016 05:39:51 GMT
    Last-Modified: Sun, 01 May 2016 05:35:21 GMT
    x-ambry-um-description: Demonstration Image
    Content-Length: 0
###### Replicas
    HTTP/1.1 200 OK
    Content-Type: application/json
    Content-Length: 48
    
    {"replicas":["Replica[localhost:15088:/tmp/1]"]}
***
### HEAD
#### Description
This API gets the blob properties of the blob represented by the supplied blob ID.
#### API
    HEAD /<ambry-id>
| Parameter | Type | Required? | Description |
| --- | --- | --- | --- |
| ambry-id | String | Yes | The ID of the blob whose properties are requested |

| Request Header | Type | Required? | Description |
| --- | --- | --- | --- |
| x-ambry-get-option | String | No | See [[options|Admin-API#get-options]].|
#### Returns
The blob properties of the blob as response headers.
##### _Success response_
A successful response is indicated by the status code `200 OK`. The response will also contain headers that describe the properties of the blob.

| Response Header | Type | Description |
| --- | --- | --- |
| x-ambry-blob-size | Long | The size of the blob |
| x-ambry-service-id | String | The ID of the service that uploaded the blob |
| x-ambry-content-type | String | The type of content in the blob |
| x-ambry-private | Boolean | `true` if the blob is private. `false` if the blob is public |
| x-ambry-creation-time | Long | The time at which the blob was created |
| x-ambry-ttl (if supplied at upload)| Long | The time in seconds for which the blob is valid from its creation time |
| x-ambry-owner-id (if supplied at upload) | String | The owner of the blob. |
##### _Failure response_
See [[standard error codes|Admin-API#standard-error-codes]].  
#### Sample Response
    HTTP/1.1 200 OK
    Date: Sun, 01 May 2016 05:41:12 GMT
    Last-Modified: Sun, 01 May 2016 05:35:21 GMT
    Content-Length: 2000
    Content-Type: image/gif
    x-ambry-blob-size: 2000
    x-ambry-service-id: API-Demo
    x-ambry-creation-time: Sun, 01 May 2016 05:35:21 GMT
    x-ambry-private: false
    x-ambry-content-type: image/gif
    x-ambry-owner-id: demo-user
***
### DELETE
#### Description
This API deletes the blob represented by the supplied blob ID.
#### API
    DELETE /<ambry-id>
| Parameter | Type | Required? | Description |
| --- | --- | --- | --- |
| ambry-id | String | Yes | The ID of the blob that has to be deleted|
#### Returns
##### _Success response_
Success is indicated by the status code `202 Accepted`. Note that deleting blobs that are already deleted will succeed without any errors.
##### _Failure response_
See [[standard error codes|Admin-API#standard-error-codes]]. 
#### Sample Response
    HTTP/1.1 202 Accepted
    Date: Sun, 01 May 2016 05:44:04 GMT
    Content-Length: 0
***
### Health Check
#### Description
This API can be used to check the status of the admin. Status here refers to the admin's ability to answer requests.
#### API
    GET /healthCheck
#### Returns
##### _Success response_
A successful response will be returned with a status code of `200 OK` and the body of the response will contain the status of admin - GOOD/BAD.
##### _Failure response_
None
#### Sample Response
    HTTP/1.1 200 OK
    Content-Length: 4

    GOOD
***
#### Get Options
| Option| Description |
| --- | --- |
| None | No special options (default) |
| Include_Deleted_Blobs | Returns the data of the blob even if it has been deleted (see note) |
| Include_Expired_Blobs | Returns the data of the blob even if it has expired (see note) |
| Include_All | Returns the data of the blob even if it has been deleted or has expired (see note) |
Note: Deleted or expired blobs may have been cleaned up by the storage due to hard delete or compaction and the data may no longer be available. These options return the blob *if* it is still exists on the storage server.
#### Standard Error Codes
| Status Code | Description |
| --- | --- |
| `400 Bad_Request` | The request does not contain required parameters or has incorrect parameters. The body of the response usually has a helpful error message |
| `401 Unauthorized` | The request does not contain enough information to authenticate the operation |
| `403 Forbidden` | The requested blob cannot be served either because the user is not authorized or the resource is dirty |
| `404 Not_Found` | The requested resource was not found |
| `407 Proxy_Authentication_Required` | The resource cannot be served just yet because it (or the user) needs proxy authentication |
| `410 Gone` | The requested resource is either deleted or has expired |
| `500 Internal_Server_Error` | The server experienced an error while serving the request |
#### Common Failure Headers
| Response Header | Type | Description |
| --- | --- | --- |
| x-ambry-failure-reason | String | If the status code was `400`, contains the reason|
 