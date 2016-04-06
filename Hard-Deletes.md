### Introduction

Currently, Ambry does not support compaction but provides the ability to hard delete. This is a common requirement for IDPC. The basic idea is to zero out the blobs on disk. This support will be replaced by the compaction support in the future.

### Challenges

The requirement to zero out blobs poses some challenges

  * The store only appends to the log and the writes are all sequential. For hard delete, it needs to perform random writes. We solve this by introducing a new Write method that does write at a specified offset.
  * A Get operation runs without any locks as writes always happen at the end (and the index is updated after the write, so a get can never try to read a blob that is being written by Puts/Deletes). With hard deletes, writes can happen anywhere in the Log, store needs to ensure that Hard Deletes do not interfere with Gets. At the same time, Gets should never be blocked. We solve this by ensuring that a hard delete never runs on recently deleted entries. A sufficiently old delete will not be a problem as gets for a deleted blob do not normally reach the log.
  * Store must also ensure that the Log never gets corrupted:
    - Adjacent blobs should not be affected during regular operations nor due to a crash.
    - Any incomplete writes due to a crash should be fixable during recovery.

### Design

The basic idea is to have an asynchronous "hard delete" thread per store that does the following periodically:

  1. Scans and finds delete records in the index
  1. Forms a readSet using these ids and the original offsets (which are offsets for the corresponding put entries) for the blobs that need to be hard deleted
  1. Reads these put entries from the log and replaces them with zeroed out entries.

**Getting the BlobReadOptions for the Original record**

Note that delete records today do not store the size of the original record. It only stores the original record's start offset. In order to be able to write to those original entries, the store now needs to know the size of those entries. We do this by using a MessageStore component (we use the same MessageStoreHardDelete component) that will go to the offset, read the header and figure out the payload size and constructs a BlobReadOptions using that.

**Scanning the Index**

Tokens

The Token class that is used for replication is used to track hard deletes as well. Two separate tokens are maintained in order to ensure that any ongoing hard deletes during a crash are completed during subsequent recovery: a startToken and endToken. The range of entries being processed during an iteration is (startToken, endToken]. Periodically, the tokens are persisted. We ensure two things at all times as far as the persisted range (startToken, endToken) is concerned:

  1. All the hard delete in progress at any time is for an entry within the range represented by the two tokens. Additionally, none of them are beyond the last persisted endToken
  1. The hard deletes that are done for entries corresponding to the startToken in the persisted file have been flushed to the log first.


Simply put, the logic is this
    `Start at the current token S. (Initially the token would represent 0).`
    `(E, entries) = findDeletedEntriesSince(S).`
    `Persist (S', E) // so during recovery we know where to stop (we will see what S' is below).`
    `performHardDelete(entries) // this is going on for (S, E]`
    `set S = E`
    `Index Persistor runs in the background and`
        `sets S' = S`
        `flushes log (so everything upto S' is surely flushed in the log)`
        `persists (S',E)`

To reiterate, the guarantees provided are that for any persisted token pair (S', E):

    1. All the hard deletes till point S' have been flushed in the log; and
    1. Ongoing hard deletes are between S' and E, so during recovery this is the range to be covered.

**findDeletedEntriesSince()**

This method is used to scan the index from a specified token and return the metadata of a set of deleted entries. It takes three parameters

    token: the token from which entries are to be retrieved from the index.
    size: the max size of entries to be returned.
    endTime: any index segment whose last modified time is greater than this time will not be scanned.

This method will function very similar to findEntriesSince() that is used for replication. The difference here is that for hard deletes we are only interested in the deleted entries in the index/log. findDeletedEntriesSince() will scan the index (limited by the hard delete scan size set in config), and return all the delete entries found in the scan. It takes an endTime which is used as follows: Entries from a segment whose last modified time is more recent than this endtime will not be fetched, and the token will not move further until the condition changes. This way, the recent deletes are not hard deleted.

**Performing hard deletes**

Given a set of ids, hard deletes are done as follows:

    1. Prepare a readSet for the given ids that use the OriginalOffset for the entries (and not the offsets, as the put record is at the original offset). Since the size of the original blob that is deleted is not kept in the index, we currently use an helper method from the hardDelete class that will actually read the header from the original offset in the log and populate the size in the readSet (this is described in section 3.1). We do this to keep the readSet interface clean.
    1. Provide the readSet to BlobStoreHardDelete class which is a class in message format layer that implements the MessageStoreHardDelete interface, and get an iterator.
    1. Use the iterator to read and write replacement records into the log. The iterator essentially iterates over the entries in the readSet, verifies them by reading them and provides a replacement message with the userMetadata and the blob content zeroed out which is then used by the store to write back to the log at the same offsets. If the read fails due to CRC mismatch or anything else, those entries are skipped (which is fine as that means they are already unreadable). The blob properties are kept in tact.

**Recovery**

During startup, we need to reprocess entries that were hard deleted but not yet flushed, in order to ensure the log is in a good state. To fix this, simply put, the cleanup thread will read the start and end tokens from the persisted token file and the hard deletes are performed for entries within that range before the store is opened.

In the sections above we saw how in order to do hard deletes, we have to first go to the log and fetch the size of the entry to create BlobReadOptions that is then used while doing hard deletes. Now, during a crash recovery, because entries could be corrupted in the range on which recovery is to be done (due to previous hard deletes possibly not getting flushed at the time of the crash), we maintain enough information in the persisted tokens to redo the recovery without actually having to read from the log again. The persisted token, therefore has more than just the start and end tokens. The token format is as follows

 `--`
 `token_version`
 `startTokenForRecovery`
 `endTokenForRecovery`
 `numBlobsInRange`
 `--`
 `blob1_blobReadOptions {version, offset, sz, ttl, key}`
 `blob2_blobReadOptions`
 `....`
 `blobN_blobReadOptions`
 `--`
 `length_of_blob1_messageStoreRecoveryInfo`
 `blob1_messageStoreRecoveryInfo {headerVersion, userMetadataVersion, userMetadataSize, blobRecordVersion, blobStreamSize}`
 `length_of_blob2_messageStoreRecoveryInfo`
 `blob2_messageStoreRecoveryInfo`
 `....`
 `length_of_blobN_messageStoreRecoveryInfo`
 `blobN_messageStoreRecoveryInfo`
 `crc`
 `---`

The blob read options are interpreted by the index. The messageStoreRecoveryInfo is interpreted and stored by the messageStoreHardDelete component. Using these, the hard deletes during recovery do not first go and read the log - they use the persisted information to directly perform hard deletes.

In order to ensure that hard deletes do not interfere with regular recovery, we make sure that hard deletes will never run on the recently written entries. The store.message.retention.days config parameter has a minimum threshold of 1 day - which means that an entry can at best be eligible for hard deletes one day after it is written. This is plenty of time for that entry to be flushed and the index to be updated so that regular recovery will only work past that point. This way we ensure that hard deletes do not ever run on unflushed regular writes and never interferes with regular recovery.

**Support for retrieving deleted objects**

The ability at the store api level to read deleted objects is now introduced (as that is necessary for unit testing hard deletes anyway). This hasn't been propagated to the frontends at this time.

**Throttling**

The throttling logic is straightforward. In every iteration, after the hard delete is done for a record, we make a call to the throttling library to throttle down (sleep) if the user specified I/O rate allowed for the hard deletes on the store is reached or exceeded. Additionally, if the hard deletes is all caught up, the thread will schedule itself to be run after a fixed interval. Also note that throttling will be disabled during recovery, so as to not slow down the recovery process.

**End Point for Scans**

A thing to note is that the store does not keep any information about when entries were created or deleted. The best estimate that we can use is the segment's last modified time. The findEntries logic will therefore make use of the segment's last modified time to determine the "age" of the entries in that segment. The tokens can even enter the journal, but will use the the entry's segment's last modified time to determine whether those should be included in the scan or not.

The last modified time of a segment is updated every time an entry is added to it (put/delete). During startup, the file's last modified time is used to initialize it. This way, the scan will work even with read-only segments as the last modified time will not get updated for them and all deleted entries will eventually be hard deleted.

**Replica Recreation and Rescrub**

When a replica is recreated, simply delete the checkpoint files as well so we begin from 0 again. Note that the end point logic is affected when replicas are recreated as new segments will be created and the last modified time will be more recent. However, this should be okay as the very old deleted entries will not have been added to these segments anyway as part of the recreation.

For rescrubbing, shutdown ambry-server, delete the cleanuptoken file within the datadir and restart the data directory. Hard deletes will start scanning from 0 again.