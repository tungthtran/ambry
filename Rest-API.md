At a high level, Ambry supports POST, GET, DELETE and HEAD. This document describes all the APIs in detail.
***
### POST
#### Description
<to-be-filled-in>
#### API Call
    POST /
    <describe params>
#### Returns
##### _Success response_
<what is success and what to look for>
##### _Failure response_
<standard codes and what they mean for this API>
#### Sample Response
    <sample good response>
***
### GET
#### Description
<to-be-filled-in>
    Sub-resources: BlobInfo, UserMetadata
#### API Call
    GET /<ambry-id>/<sub-resource>
    <describe params>
#### Returns
###### Without sub-resources
###### BlobInfo
###### UserMetadata
##### _Success response_
<what is success and what to look for>
###### Without sub-resources
###### BlobInfo
###### UserMetadata
##### _Failure response_
<standard codes and what they mean for this API>
#### Sample Response
    <sample good response>
###### Without sub-resources
###### BlobInfo
###### UserMetadata
***
### HEAD
#### Description
<to-be-filled-in>
#### API Call
    HEAD /<ambry-id>
    <describe params>
#### Returns
##### _Success response_
<what is success and what to look for>
##### _Failure response_
<standard codes and what they mean for this API>
#### Sample Response
    <sample good response>
***
### DELETE
#### Description
<to-be-filled-in>
#### API Call
    DELETE /<ambry-id>
    <describe params>
#### Returns
##### _Success response_
<what is success and what to look for>
##### _Failure response_
<standard codes and what they mean for this API>
#### Sample Response
    <sample good response>
***
### Health Check
#### Basics
    Rate Limited: No
    URL: /healthCheck
#### Description
This API can be used to check the status of the frontend. Status here refers to the frontend's ability to answer requests.
#### API Call
    GET /healthCheck
#### Returns
##### _Success response_
Status of frontend - GOOD/BAD.
##### _Failure response_
None
#### Sample Response
    HTTP/1.1 200 OK
    Content-Length: 4

    GOOD


 