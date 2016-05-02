**Frontend Metrics**

             **Mbean**	                        |                          **Description**    
    putBlobOperationLatencyInMs                          The latency of the put blob operation measured at the frontend
    deleteBlobOperationLatencyInMs                       The latency of the delete blob operation measured at the frontend
    getBlobPropertiesOperationLatencyInMs                The latency of get blob properties operation measured at frontend
    getBlobUserMetadataOperationLatencyInMs              The latency of get blob usermetadata operation measured at frontend
    getBlobOperationLatencyInMs                          The latency of get blob operation measured at frontend
    putBlobOperationRate                                 The rate at which put blob operation calls happen at the frontend
    deleteBlobOperationRate                              The rate at which delete blob operation calls happen at frontend
    getBlobPropertiesOperationRate                       The rate at which getblobproperties operation happen at frontend
    getBlobUserMetadataOperationRate                     The rate at which getBlobUsermetadata operation happen at frontend
    getBlobOperationRate                                 The rate at which getBlob operation happen at frontend
    operationExceptionRate                               The rate at which operation calls have an error
    putBlobError                                         The error rate for put blob operations
    deleteBlobError                                      The error rate for delete blob operations
    getBlobPropertiesError                               The error rate for get blob properties operations
    getBlobUserMetadataError                             The error rate for get blob usermetadata operations
    getBlobError                                         The error rate for get blob operations            
    ambryUnavailableError                                The error rate when ambry is not available 
    operationTimedOutError                               The error rate when operation times out
    invalidBlobIdError                                   The rate at which invalid blob Ids are specified in a get or delete
    insufficientCapacityError                            The rate at which put requests fail when capacity is not available
    blobTooLargeError                                    The rate at which put fails due to blobs being too large
    blobDoesNotExistError                                The rate at which get and delete fail because blob does not exist
    blobDeletedError                                     The rate at which get and delete fail because blob is deleted
    blobExpiredError                                     The rate at which get and delete fail because blob is expired
    corruptionError                                      The rate at which get fails because the blob is corrupt
    successfulCrossColoProxyCallCount                    The rate at which successful calls happen cross colo to fetch blob


**Server Metrics**

             **Mbean**	                        |                          **Description**  
    putBlobRequestQueueTimeInMs                         Time spent by put requests in the request queue  
    putBlobProcessingTimeInMs                           Time spent processing the put request  
    putBlobResponseQueueTimeInMs                        Time spent by the response of put in the response queue  
    putBlobSendTimeInMs                                 Time spent sending the response for put  
    putBlobTotalTimeInMs                                Total time for put  
    getBlobRequestQueueTimeInMs                         Time spent by get blob request in request queue    
    getBlobProcessingTimeInMs                           Time spent by get blob in processing  
    getBlobResponseQueueTimeInMs                        Time spent by the response of get blob in the response queue  
    getBlobSendTimeInMs                                 Time spent sending the response for get blob  
    getBlobTotalTimeInMs                                Total time for get blob  
    getBlobPropertiesRequestQueueTimeInMs               Time spent by get blob properties request in request queue  
    getBlobPropertiesProcessingTimeInMs                 Time spent by get blob properties in processing  
    getBlobPropertiesResponseQueueTimeInMs              Time spent by response of get blob properties in the response queue  
    getBlobPropertiesSendTimeInMs                       Time spent sending the response for get blob properties  
    getBlobPropertiesTotalTimeInMs                      Total time for get blob properties  
    getBlobUserMetadataRequestQueueTimeInMs             Time spent by get blob usermetadata request in request queue  
    getBlobUserMetadataProcessingTimeInMs               Time spent by get blob usermetadata in processing  
    getBlobUserMetadataResponseQueueTimeInMs            Time spent by response of get blobusermetadata in the response queue  
    getBlobUserMetadataSendTimeInMs                     Time spent sending the response for get blob usermetadata  
    getBlobUserMetadataTotalTimeInMs                    Total time for get blob usermetadata  
    deleteBlobRequestQueueTimeInMs                      Time spent by delete blob request in request queue  
    deleteBlobProcessingTimeInMs                        Time spent by delete blob in processing  
    deleteBlobResponseQueueTimeInMs                     Time spent by the response of delete blob in the response queue  
    deleteBlobSendTimeInMs                              Time spent sending the response for delete blob usermetadata  
    deleteBlobTotalTimeInMs                             Total time for delete blob  
    replicaMetadataRequestQueueTimeInMs                 Time spent by replica metadata request in request queue  
    replicaMetadataRequestProcessingTimeInMs            Time spent by replica metadata request in processing  
    replicaMetadataResponseQueueTimeInMs                Time spent by the response of replica metadata in the response queue  
    replicaMetadataSendTimeInMs                         Time spent sending the response for replica metadata usermetadata  
    replicaMetadataTotalTimeInMs                        Total time for replica metadata request  
    putBlobRequestRate                                  Rate at which put blobs occur
    getBlobRequestRate                                  Rate at which get blobs occur
    getBlobPropertiesRequestRate                        Rate at which get blob properties occur
    getBlobUserMetadataRequestRate                      Rate at which get blob usermetadata occur
    deleteBlobRequestRate                               Rate at which delete blob occur
    replicaMetadataRequestRate                          Rate at which replicametadata request occur
    partitionUnknownError                               Error when partition is not known
    diskUnavailableError                                Error when disk is not available
    partitionReadOnlyError                              Error when blob is put in a partition that is read only
    storeIOError                                        Error when an IO error occurs in the store
    unExpectedStorePutError                             Unknown error during Put
    unExpectedStoreGetError                             Unknown error during Get
    unExpectedStoreDeleteError                          Unknown error during delete
    unExpectedStoreFindEntriesError                     Unknown error when find entries occur                                 
    idAlreadyExistError                                 Error when ID already exist while trying to put
    dataCorruptError                                    Error when there is a disk corrupt
    unknownFormatError                                  Error when the format is error
    idNotFoundError                                     Error when id is not found                       
    idDeletedError                                      Error when id is already deleted

**Replication Metrics**

             **Mbean**	                        |                          **Description**  

    interColoReplicationBytesRate                       The rate at which bytes are transferred between datacenters  
    intraColoReplicationBytesRate                       The rate at which bytes are transferred within a datacenter  
    interColoMetadataExchangeCount                      The rate at which metadata exchange happens between datacenters  
    intraColoMetadataExchangeCount                      The rate at which metadata exchange happens within a datacenter  
    interColoBlobsReplicatedCount                       The rate at which blobs are replicated between datacenters  
    intraColoBlobsReplicatedCount                       The rate at which blobs are replicated within a datacenter  
    replicationErrors                                   The rate at which replication errors occur
    replicationInvalidMessageStreamErrorCount           The rate at which invalid messages are seen during replication
    interColoReplicationLatency                         The time taken to replicate bytes end to end between datacenters
    intraColoReplicationLatency                         The time taken to replicate bytes end to end within a datacenter
    interColoExchangeMetadataTime                       The time taken to exchange metadata information between datacenters
    intraColoExchangeMetadataTime                       The time taken to exchange metadata information within a datacenter
    interColoFixMissingKeysTime                         The time taken to fix missing keys between datacenters
    intraColoFixMissingKeysTime                         The time taken to fix missing keys within a datacenter
    replicaLagInBytes                                   The lag between two machines for each partition
    metadataRequestError                                The rate at which metadata request error happens during replication
    getRequestError                                     The rate at which get request error happens during replication
    localStoreError                                     The rate at which the local store write fails during replication