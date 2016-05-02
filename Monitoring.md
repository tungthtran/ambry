Frontend Metrics

Mbean	 |        Description      

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
    replicationErrors
    replicationInvalidMessageStreamErrorCount
    interColoReplicationLatency
    intraColoReplicationLatency
    interColoExchangeMetadataTime
    intraColoExchangeMetadataTime
    interColoFixMissingKeysTime
    intraColoFixMissingKeysTime
    interColoReplicationMetadataRequestTime
intraColoReplicationMetadataRequestTime
interColoReplicationWaitTime
intraColoReplicationWaitTime
interColoCheckMissingKeysTime
intraColoCheckMissingKeysTime
interColoProcessMetadataResponseTime
intraColoProcessMetadataResponseTime
interColoGetRequestTime
intraColoGetRequestTime
interColoBatchStoreWriteTime
intraColoBatchStoreWriteTime
interColoTotalReplicationTime
intraColoTotalReplicationTime
numberOfIntraDCReplicaThreads
numberOfInterDCReplicaThreads
replicaLagInBytes 
metadataRequestError
getRequestError
localStoreError