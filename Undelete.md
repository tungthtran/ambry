# Description
Ambry blobs can get erroneously deleted by an authorized application. To recover from this mistake, applications have to re-upload the same blob. However, it's often impossible to re-upload the blob since they are uploaded by users. And even if re-uploading is possible, it will generate a new blob id, which might also require application to update its owner database to store the new id. With those shortcoming in mind, we designed a new feature call "undelete" to help application to recovery from mistakenly deleting a blob.
# New Message Format
Undelete a deleted blob would break the intrinsic order to different types of message, where PUT precedes TTL_UPDATE, TTL_UPDATE precedes DELETE. A undeleted blob can be deleted again, so there is no way to tell the order of UNDELETE and DELETE messages. In addition to that, an undeleted blob can have its ttl updated, so there is no way to tell the order of TTL_UPDATE and DELETE message neither.

To solve the issue of ordering, we added a new field to Message Header persisted in the log, lifeVersion. A lifeVersion will be initiated as 0 when a blob is created (In a PUT message), and incremented by 1 every time there is an UNDELETE message. Messages with the same lifeVersion would restore intrinsic order, shown below. 

    PUT/UNDELETE -> TTL_UPDATE -> DELETE
Messages with different lifeVersion are ordered the by the lifeVersion. A message with lifeVersion being 0 is always prior to messages with lifeVersion being 1.

This ordering is extremely important when servers replicating blobs from each other. We will talk about replication later.
# Global Quorum
# Replication
# Compaction
# Resources