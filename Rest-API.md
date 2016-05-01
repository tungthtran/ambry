At a high level, Ambry supports POST, GET, DELETE and HEAD. This document describes all the APIs in detail.
***
### POST
#### Description
This API uploads a blob to Ambry. The call should also include some necessary blob properties and can include optional user metadata. The API returns a resource ID that can be used to access the blob.
#### API Call
    POST /
| Header | Type | Description |
| --- | --- | --- |
| x-ambry-blob-size (required) | Long | The size of the blob being uploaded |
| x-ambry-service-id (required) | String | The ID of the service that is uploading the blob |
| x-ambry-content-type (required) | String | The type of content in the blob |
| x-ambry-ttl (optional)| Long | The time in seconds for which the blob is valid. Defaults to -1 (infinite validity) |
| x-ambry-private (optional)| Boolean | Makes the blob private if set to "true". Defaults to "false" (blob is public) |
| x-ambry-owner-id (optional) | String | The owner of the blob. |
| x-ambry-um- (optional) | String | User metadata headers prefix. Any number of headers with this prefix are allowed. |
#### Returns
The location of the created blob on success.
##### _Success response_
The status code `201 Created` indicates that the blob was successfully uploaded. The response also contains the "Location" header that contains the ID of the blob.
##### _Failure response_
See our [[standard error codes|Rest-API#standard-error-codes]]. 
#### Sample Response
    <sample good response>
***
### GET
#### Description
This API gets the content of the blob represented by the blob ID. When used with sub-resources, it gets user metadata (and optionally blob properties) instead of the actual content of the blob.
#### API Call
    GET /<ambry-id>/<sub-resource>
    Sub-resources: BlobInfo, UserMetadata

| Parameter | Type | Description |
| --- | --- | --- |
| ambry-id (required) | String | The ID of the blob whose content is required |
| sub-resource (optional) | String | One of the listed sub-resources |
#### Returns
###### Without sub-resources
The content of the blob.
###### UserMetadata
The user metadata as response headers.
###### BlobInfo
The user metadata and blob properties as response headers.
##### _Success response_
A successful response is indicated by the status code `200 Ok`. 
###### Without sub-resources
The body of the response will contain the content of the blob.
###### UserMetadata
The response headers will contain the user metadata that was uploaded (if any).

| Response Header | Type | Description |
| --- | --- | --- |
| x-ambry-um-* | String | Zero or more headers with this prefix that represent user metadata |
###### BlobInfo
The response headers will contain the user metadata that was uploaded (if any) and the properties of the blob.

| Response Header | Type | Description |
| --- | --- | --- |
| x-ambry-blob-size | Long | The size of the blob |
| x-ambry-service-id | String | The ID of the service that uploaded the blob |
| x-ambry-content-type | String | The type of content in the blob |
| x-ambry-private | Boolean | "true" if the blob is private. "false" if the blob is public |
| x-ambry-creation-time | Long | The time at which the blob was created |
| x-ambry-ttl (if supplied at upload)| Long | The time in seconds for which the blob is valid from its creation time |
| x-ambry-owner-id (if supplied at upload) | String | The owner of the blob. |
| x-ambry-um-* | String | Zero or more headers with this prefix that represent user metadata |
##### _Failure response_
See our [[standard error codes|Rest-API#standard-error-codes]].  
<standard codes and what they mean for this API>
#### Sample Response
    <sample good response>
###### Without sub-resources
###### BlobInfo
###### UserMetadata
***
### HEAD
#### Description
This API gets the blob properties of the blob represented by the supplied blob ID.
#### API Call
    HEAD /<ambry-id>
| Parameter | Type | Description |
| --- | --- | --- |
| ambry-id (required) | String | The ID of the blob whose properties are required |
#### Returns
The blob properties of the blob as response headers.
##### _Success response_
A successful response is indicated by the status code `200 Ok`. The response will also contain headers that describe the properties of the blob.

| Response Header | Type | Description |
| --- | --- | --- |
| x-ambry-blob-size | Long | The size of the blob |
| x-ambry-service-id | String | The ID of the service that uploaded the blob |
| x-ambry-content-type | String | The type of content in the blob |
| x-ambry-private | Boolean | "true" if the blob is private. "false" if the blob is public |
| x-ambry-creation-time | Long | The time at which the blob was created |
| x-ambry-ttl (if supplied at upload)| Long | The time in seconds for which the blob is valid from its creation time |
| x-ambry-owner-id (if supplied at upload) | String | The owner of the blob. |
The blob content should be POSTED as a stream of bytes whose length is equal to "x-ambry-blob-size".
##### _Failure response_
See our [[standard error codes|Rest-API#standard-error-codes]].  
<standard codes and what they mean for this API>
#### Sample Response
    <sample good response>
***
### DELETE
#### Description
This API deletes the blob represented by the supplied blob ID.
#### API Call
    DELETE /<ambry-id>
| Parameter | Type | Description |
| --- | --- | --- |
| ambry-id (required) | String | The ID of the blob that has to be deleted|
#### Returns
##### _Success response_
Success is indicated by the status code `202 Accepted`. Note that deleting blobs that are already deleted will succeed without any errors.
##### _Failure response_
See our [[standard error codes|Rest-API#standard-error-codes]]. 
<standard codes and what they mean for this API>
#### Sample Response
    <sample good response>
***
### Health Check
#### Description
This API can be used to check the status of the frontend. Status here refers to the frontend's ability to answer requests.
#### API Call
    GET /healthCheck
#### Returns
##### _Success response_
A successful response will be returned with a status code of `200 Ok` and the body of the response will contain the status of frontend - GOOD/BAD.
##### _Failure response_
None
#### Sample Response
    HTTP/1.1 200 OK
    Content-Length: 4

    GOOD
***
#### Standard Error Codes
| Status Code | Description |
| --- | --- |
| `400 Bad_Request` | The request does not contain required parameters or has incorrect parameters |
| `401 Unauthorized` | The request does not contain enough information to authenticate the operation |
| `403 Forbidden` | The required blob cannot be served either because the user is not authorized or the resource is dirty |
| `404 Not_Found` | The requested resource was not found |
| `407 Proxy_Authentication_Required` | The resource cannot be served just yet because it (or the user) needs proxy authentication |
| `410 Gone` | The requested resource is either deleted or has expired |
| `500 Internal_Server_Error` | The server experienced an error while serving the request |
 