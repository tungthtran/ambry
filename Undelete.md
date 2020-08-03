# Description
Ambry blobs can get erroneously deleted by an authorized application. To recover from this mistake, applications have to re-upload the same blob. However, it's often impossible to re-upload the blob since they are uploaded by users. And even if re-uploading is possible, it will generate a new blob id, which might also require application to update its owner database to store the new id. With those shortcoming in mind, we designed a new feature call "undelete" to help application to recovery from mistakenly deleting a blob.
# New Message Format
Undelete a deleted blob would break the intrinsic order to different types of message, where PUT precedes TTL_UPDATE, TTL_UPDATE precedes DELETE. A undeleted blob can be deleted again, so there is no way to tell the order of UNDELETE and DELETE messages. In addition to that, an undeleted blob can have its ttl updated, so there is no way to tell the order of TTL_UPDATE and DELETE message neither.

To solve the issue of ordering, we added a new field to Message Header persisted in the log, lifeVersion. A lifeVersion will be initiated as 0 when a blob is created (In a PUT message), and incremented by 1 every time there is an UNDELETE message. Messages with the same lifeVersion would restore intrinsic order, shown below. 

    PUT/UNDELETE -> TTL_UPDATE -> DELETE
Messages with different lifeVersion are ordered the by the lifeVersion. A message with lifeVersion being 0 is always prior to messages with lifeVersion being 1.

This ordering is extremely important when servers replicating blobs from each other. We will talk about replication later.
# Global Quorum
Undelete operation has to reach global quorum in order to mark the success of this operation, unlike other mutations, they only have to reach local colo quorum. The reason for global quorum is that undelete operation would increment lifeVersion locally in ambry servers, and there is no communication for these servers to exchange new lifeVersions.

For example, given three replicas in each colo and we have two colo, this is what a blob looks like in each replica.

    Replica1 at colo1: PUT, DELETE(0), UNDELETE(1)
    Replica2 at colo1: PUT, DELETE(0), UNDELETE(1)
    Replica3 at colo2: PUT                          DELETE(0)
    Replica4 at colo2: PUT                          DELETE(0)

When frontend in colo2 is deleting this blob, it will succeed and replica 3 and 4 would have DELETE message at lifeVersion 0. This is an critical error since when replica 3 and 4 replicating from replica 1 and 2, they will treat UNDELETE message at lifeVersion 1 as more recent than local DELETE message since when messages' order is determined by lifeVersion first. This will replicate UNDELETE to replica 3 and 4 and change the final state of the this blob, which is not what users are expecting. 

To fix this issue, UNDELETE has to reach global quorum and succeed in all colos. With undelete succeeding in all colos, the aforementioned case would not happen.
# Replication
The basic replication logic will not change. ReplicationThread would still send Metadata request first to get fetch each blob's final state from peer replicas, and apply changes to local blob store accordingly. The only difference with undelete, is how to reconcile final states of the same blob from remote replica and local blob store.

Before undelete, orders of different messages are determined by the message type, DELETE > TTL_UPDATE > PUT. So they are two final states to reconcile
1. is blob deleted
2. is blob ttl updated

After undelete, orders of different messages are determined first by lifeVesion, then message type. So they are three final states to reconcile
1. lifeVersion
2. is blob deleted
3. is blob ttl updated

When local blob's lifeVersion is greater than the remote replica, then there is nothing to be done for this blob, for local replica has more operations than remote replicas. When local blob's lifeVersion equals to remote replica, then replication falls back to the same logic without undelete. When local blob's lifeVersion is less than remote replica, then it applies other states with remote replica's lifeVersion.

For example, this is two replicas' operation history for the same blob

    Replica1: PUT, DELETE(0), UNDELETE(1), TTL_UPDATE(1), DELETE(1)
    Replica2: PUT, TTL_UPDATE(0)

When replica2 replicating from replica1, the local states are
1. lifeVersion == 0
2. is blob deleted == false
3. is blob ttl updated == true

and the remote states are
1. lifeVersion == 1
2. is blob deleted == false
3. is blob ttl updated == true
Replication has to apply a DELETE message with lifeVersion 1 to local blob store, and local states will be the same as the remote state.

# Compaction
The basic compaction logic will not change. Compaction would still compact blobs that are expired and deleted out of retention duration. However, since DELETE is no longer the final message of a blob, compaction changes its approach of finding invalid blobs.

When compaction is triggered on a replica, it iterate through all the messages in under-compacted logs. For each message, it finds the final state of this blob and determine if current message should be compacted or not. For example, if current message is UNDELETE(1) and the final state of this blob is DELETE(2), then current messages should be compacted. The compaction rule is described in the table below.

    | Current IndexValue  | Latest IndexValue | Is Valid                                     |
    | --------------------+-------------------+----------------------------------------------|
    | Put(verion c)       | Put(version f)    | isExpired(Pc)?false:true                     |
    |                     | Delete(f)         | reachRetention(Df)||isExpired(Df)?false:true |
    |                     | Undelete(f)       | isExpired(Uf)?false:true                     |
    | --------------------+-------------------+----------------------------------------------|
    | TtlUpdate(c)        | Put(f)            | Exception                                    |
    |                     | Delete(f)         | reachRetention(Df)?false:true                |
    |                     | Undelete(f)       | true                                         |
    | --------------------+-------------------+----------------------------------------------|
    | Delete(c)           | Put(f)            | Exception                                    |
    |                     | Delete(f)         | c==f?true:false                              |
    |                     | Undelete(f)       | false                                        |
    | --------------------+-------------------+----------------------------------------------|
    | Undelete(c)         | Put(f)            | Exception                                    |
    |                     | Delete(f)         | false                                        |
    |                     | Undelete(f)       | c==f&&!isExpired(Uf)?true:false              |
    | ---------------------------------------------------------------------------------------

# Configuration
* **`store.compaction.filter`**: The filter to use to get valid entries for compactor. By default, it uses the filter that ignore UNDELETE message, to enable undelete, use `IndexSegmentValidEntryFilterWithUndelete`.
* **`server.handle.undelete.request.enabled`**: Set it true to enable ambry server to handle undelete requests from frontends.
* **`frontend.enable.undelete`**: Set it true to enable ambry frontend to handle undelete requests from clients.
# Resources
This wiki introduces and describes how the new undelete operation affects replication and compaction. For more details on the design and actual implementation, please refer to the following resources:
* [Undelete design](https://docs.google.com/document/d/1uOUzuu70Akgmlr_J-g3ScO4E7gA8mxnjn868wZ_iS_0/edit?usp=sharing)
* [Undelete implementation](https://docs.google.com/document/d/1rm6NBOUeZRMbuF26116lerytiW3tde38I23cZVr3SG0/edit?usp=sharing)