The replication protocol uses a token to bookmark a specific point in the remote store. These tokens are returned by the remote stores on every replication roundtrip. The tokens are saved at some interval to ensure that when replication resumes after a restart, it can start from where it left previously. 

Replication Token Persist File

     ------------------------------------------------------
    |  Version  | Persist File Entries (1...n) |   CRC     | 
    | (2 bytes) |     (n bytes)                | (8 bytes) |
     ------------------------------------------------------
    Version                   - The version of the token persist file format
    PersistFileEntries (1..n) - There are n entries where each entry contains information about a token
    CRC                       - The checksum of the whole file

Persist File Entry

     ---------------------------------------------------------------------------------------
    | PartitionId |  Hostname  | ReplicaID  |    Port   | Bytes Read From Store | FindToken |
    |  (n bytes)  |  (n bytes) | (n bytes)  | (4 bytes) |     (8 bytes)         | (n bytes) |
     ---------------------------------------------------------------------------------------
    PartitionId        - The partition for which the entry pertains to
    Hostname           - The remote host from which replication is in progress
    ReplicaId          - The replica of the partition that is being replicated
    Port               - The remote port that hosts the replica
    BytesReadFromStore - The total bytes read from the remote store 
    FindToken          - The bookmark from the remote store

FindToken 

     -----------------------------------------------------------------------------------------
    |    Size   |  Version  |  Session ID  |   Offset   |  Index Start  Offset  |  Store Key  |
    | (8 bytes) | (2 bytes) |  (8 bytes)   |  (8 bytes) |        (8 bytes)      |    n bytes  |
     -----------------------------------------------------------------------------------------