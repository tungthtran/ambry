The replication protocol uses a token to bookmark a specific point in the remote store. These tokens are returned by the remote stores on every replication roundtrip. The tokens are saved at some interval to ensure that when replication resumes after a restart, it can start from where it left previously. 

Replication Token Persist File

     ------------------------------------------------------
    |  Version  | Persist File Entries (1...n) |   CRC     | 
    | (2 bytes) |     (n bytes)                | (8 bytes) |
     ------------------------------------------------------

Persist File Entry

     ---------------------------------------------------------------------------------------
    | PartitionId |  Hostname  | ReplicaID  |    Port   | Bytes Read From Store | FindToken |
    |  (n bytes)  |  (n bytes) | (n bytes)  | (4 bytes) |     (8 bytes)         | (n bytes) |
     ---------------------------------------------------------------------------------------

FindToken 

     ----------------------------------------------------------------------------------------
    |  Size  |  Version  |  Session ID  |  Offset  |  Index Start  Offset  |  Store Key  |