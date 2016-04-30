The storage format for Ambry consist of a preallocated log file that stores the actual blobs and an on disk index structure to locate these blobs. The log itself is agnostic to any format and just treats everything as bytes. The persistent index however does have an in memory and on disk format. 

### In memory Index structure of each entry
Every key is mapped to the following in memory structure

    - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 
    |   size  |  log offset |  flags   |  timetolive |   originalMessageOffset  |  
    |(8 bytes)|  (8 bytes)  |  (1 byte)|  (8 bytes)  |         (8 bytes)        | 
    - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - 
    size                    - the entry size in memory
    log offset              - the offset in the log where the blob can be read
    flags                   - a byte sized flag used for marking properties. Currently used for delete.
    time to live            - the time in ms when the blob will expire
    original message offset - the offset of the original message. This is used when a new entry is added for deletes and the 
                              deleted blob needs to be located in the log.

###   On disk Index structure of each entry
    - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    | version | keysize | valuesize | fileendpointer |   key 1  | value 1  |  ...  |   key n   | value n   | crc      |
    |(2 bytes)|(4 bytes)| (4 bytes) |    (8 bytes)   | (n bytes)| (n bytes)|       | (n bytes) | (n bytes) | (8 bytes)|
    - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    version         - the index format version
    keysize         - the size of the key in this index segment
    valuesize       - the size of the value in this index segment
    fileendpointer  - the log end pointer that pertains to the index being persisted
    key n / value n - the key and value entries contained in this index segment
    crc             - the crc of the index segment content
  