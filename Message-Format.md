The message format are of the following types - PutMessageFormat, DeleteMessageFormat and HardDeleteMessageFormat. Each of these formats are created by a combination of atomic units called Message records. 

The list of message records are -

### Message Header Record

     - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    |         |           |                |                 |                 |                 |                 |            |
    | version |  life     | payload size   | Blob Property   |     Delete      |  User Metadata  |      Blob       |    Crc     |
    |(2 bytes)|  version  |  (8 bytes)     | Relative Offset | Relative Offset | Relative Offset | Relative Offset |  (8 bytes) |
    |         | (2 bytes) |                |   (4 bytes)     |   (4 bytes)     |   (4 bytes)     |   (4 bytes)     |            |
     - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 
     version         - The version of the message header
     
     life version    - The life version of the message header (explained in Undelete)

     payload size    - The size of the message payload.
                       (Blob prop record size or delete record size) + user metadata size + blob size
     
     blob property   - The offset at which the blob property record is located relative to this message. Only one of
     
     relative offset   blob property/delete relative offset field can exist. Non existence is indicated by -1
     
     delete          - The offset at which the delete record is located relative to this message. Only one of blob
     relative offset   property/delete relative offset field can exist. Non existence is indicated by -1
   
     user metadata   - The offset at which the user metadata record is located relative to this message. This exist
     relative offset   only when blob property record and blob record exist
   
     blob metadata   - The offset at which the blob record is located relative to this message. This exist only when
     relative offset   blob property record and user metadata record exist
   
     crc             - The crc of the message header

### Blob Properties Record

    - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    |         |               |               |           |            |
    | version |   property1   |   property2   |           |     Crc    |
    |(2 bytes)| (1 - n bytes) | (1 - n bytes) |   .....   |  (8 bytes) |
    |         |               |               |           |            |
    - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    version         - The version of the blob property record
   
    properties      - Variable size properties that define the blob.
   
    crc             - The crc of the blob property record


### Delete Record
    - - - - - - - - - - - - - - - - - - -
    |         |               |            |
    | version |   delete byte |    Crc     |
    |(2 bytes)|    (1 byte)   |  (8 bytes) |
    |         |               |            |
    - - - - - - - - - - - - - - - - - - -
    version         - The version of the delete record
   
    delete byte     - Takes value 0 or 1. If it is set to 1, it signifies that the blob is deleted. The field
                      is required to be able to support undelete in the future if required.
   
    crc             - The crc of the delete record


### User Metadata Record

    - - - - - - - - - - - - - - - - - - - - - - - -
    |         |           |            |            |
    | version |   size    |  content   |     Crc    |
    |(2 bytes)| (4 bytes) |  (n bytes) |  (8 bytes) |
    |         |           |            |            |
    - - - - - - - - - - - - - - - - - - - - - - - -
    version    - The version of the user metadata record
   
    size       - The size of the user metadata content
   
    content    - The actual content that represents the user metadata
   
    crc        - The crc of the user metadata record

### Blob Record V1
    - - - - - - - - - - - - - - - - - - - - - - - -
    |         |           |            |            |
    | version |   size    |  content   |     Crc    |
    |(2 bytes)| (8 bytes) |  (n bytes) |  (8 bytes) |
    |         |           |            |            |
    - - - - - - - - - - - - - - - - - - - - - - - -
    version    - The version of the blob record
   
    size       - The size of the blob content
   
    content    - The actual content that represents the blob
   
    crc        - The crc of the blob record
   
### Blob Record V2

    - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    |         |           |            |            |            |
    | version | blobType  |    size    |  content   |     Crc    |
    |(2 bytes)| (2 bytes) |  (8 bytes) |  (n bytes) |  (8 bytes) |
    |         |           |            |            |            |
    - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    version    - The version of the blob record
   
    blobType   - The type of the blob
   
    size       - The size of the blob content
   
    content    - The actual content that represents the blob
   
    crc        - The crc of the blob record

### Hard Delete Record

    - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    |         |               |            |            |          |
    | version |   no of keys  |    key1    |     key2   |  ......  |
    |(2 bytes)|    (4 bytes)  |            |            |  ......  |
    |         |               |            |            |          |
    - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    version         - The version of the metadata content record
   
    no of keys      - total number of keys
   
    key1            - first key to be part of metadata blob
   
    key2            - second key to be part of metadata blob


The message formats are the following -

### PutMessageFormat

Represents a message that consist of the blob, blob properties and user metadata.
This format is used to put a new blob into the store
 
     - - - - - - - - - - - - - -
    |     Message Header        |
     - - - - - - - - - - - - - -
    |       blob key            |
     - - - - - - - - - - - - - -
    |  Blob Properties Record   |
     - - - - - - - - - - - - - -
    |  User metadata Record     |
     - - - - - - - - - - - - - -
    |       Blob Record         |
     - - - - - - - - - - - - - -

### DeleteMessageFormat

Represents a message that consist of the delete record.
This format is used to delete a blob
 
     - - - - - - - - - - - - -
    |     Message Header      |
     - - - - - - - - - - - - -
    |       blob key          |
     - - - - - - - - - - - - -
    |      Delete Record      |
     - - - - - - - - - - - - -

### HardDeleteMessageFormat

Represents a message that consist of just the user metadata and blob content. Additionally, these fields are zeroed out.
This format is used to replace a put record's user metadata and blob content part as part of hard deleting it.
The usermetadata and blob record versions of the replacement stream will have to be the same as the versions in
the original put record.  
 
     - - - - - - - - - - - - - - - - - - -
    |           Message Header            |
     - - - - - - - - - - - - - - - - - - -
    |              blob key               |
     - - - - - - - - - - - - - - - - - - -
    |       Blob Properties Record        |
     - - - - - - - - - - - - - - - - - - -
    |  User metadata Record (Zeroed out)  |
     - - - - - - - - - - - - - - - - - - -
    |       Blob Record (Zeroed out)      |
     - - - - - - - - - - - - - - - - - - -