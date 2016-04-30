The message format are of the following types - PutMessageFormat, DeleteMessageFormat and HardDeleteMessageFormat. Each of these formats are created by a combination of atomic units called Message records. The list of message records are provided below followed by the actual formats.  

### Message Header Record

     - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    |         |                 |                 |                 |                 |                 |            |
    | version |  payload size   | Blob Property   |     Delete      |  User Metadata  |      Blob       |    Crc     |
    |(2 bytes)|   (8 bytes)     | Relative Offset | Relative Offset | Relative Offset | Relative Offset |  (8 bytes) |
    |         |                 |   (4 bytes)     |   (4 bytes)     |   (4 bytes)     |   (4 bytes)     |            |
     - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
     version         - The version of the message header
     
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


/**
   *  - - - - - - - - - - - - - - - - - - - - - - - -
   * |         |           |            |            |
   * | version |   size    |  content   |     Crc    |
   * |(2 bytes)| (4 bytes) |  (n bytes) |  (8 bytes) |
   * |         |           |            |            |
   *  - - - - - - - - - - - - - - - - - - - - - - - -
   *  version    - The version of the user metadata record
   *
   *  size       - The size of the user metadata content
   *
   *  content    - The actual content that represents the user metadata
   *
   *  crc        - The crc of the user metadata record
   *
   */


/**
   *  - - - - - - - - - - - - - - - - - - - - - - - -
   * |         |           |            |            |
   * | version |   size    |  content   |     Crc    |
   * |(2 bytes)| (8 bytes) |  (n bytes) |  (8 bytes) |
   * |         |           |            |            |
   *  - - - - - - - - - - - - - - - - - - - - - - - -
   *  version    - The version of the blob record
   *
   *  size       - The size of the blob content
   *
   *  content    - The actual content that represents the blob
   *
   *  crc        - The crc of the blob record
   *
   */


/**
   *  - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
   * |         |           |            |            |            |
   * | version | blobType  |    size    |  content   |     Crc    |
   * |(2 bytes)| (2 bytes) |  (8 bytes) |  (n bytes) |  (8 bytes) |
   * |         |           |            |            |            |
   *  - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
   *  version    - The version of the blob record
   *
   *  blobType   - The type of the blob
   *
   *  size       - The size of the blob content
   *
   *  content    - The actual content that represents the blob
   *
   *  crc        - The crc of the blob record
   *
   */


/**
   *  - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
   * |         |               |            |            |          |
   * | version |   no of keys  |    key1    |     key2   |  ......  |
   * |(2 bytes)|    (4 bytes)  |            |            |  ......  |
   * |         |               |            |            |          |
   *  - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
   *  version         - The version of the metadata content record
   *
   *  no of keys      - total number of keys
   *
   *  key1            - first key to be part of metadata blob
   *
   *  key2            - second key to be part of metadata blob
   *
   */