Feel free to take any topic that is not marked as in progress. Anyone who picks any topic needs to write a detailed wiki on the design, create an issue and add the wiki to the issue. After discussions with the group and reaching a consensus, we can proceed towards implementation.

### Rack aware (In progress)
The goal of this project is to place the replicas of the partitions in a rack aware way. This would involve making the cluster manager rack aware and updating the tooling to manage partitions (create and modify) in a rack aware way.

### Compaction
Today Ambry supports hard deletes. This involves zeroing out the bytes on the disk for the blobs that have been marked as deleted. This is largely because deletes are uncommon in a store that is used to host media. However, we would need to reclaim the storage in the future when we have more use cases where compaction will restore significant storage. This would involve identifying how to compact existing partitions while the system is running with minimal performance impact and ensuring read, writes and replication still continues to work.

### Dynamic cluster management
Currently, cluster management uses a hardware layout and partition layout files to manage the cluster. Any cluster management requires updating these files and pushing to the cluster and restarting the machines. We would like to do this dynamically and make it automated if possible. To do that, we need to move to a dynamic cluster management such as Zookeeper, Helix or a consensus library such as Raft. We would also need to introduce the notion of a controller per cluster that can execute the cluster operations and help to automate tasks in the future.

### Containers/Buckets
We would like to introduce the notion of container/bucket that would help to define a namespace for objects. Once this is defined, we could define acls, quotas etc at this level.

### Authorization/Authentication
We current support encryption between servers and between frontend and servers. However, we would need to introduce authorization and authentication for clients. This would depend on the container/bucket work. Once that is done, we would need to introduce a way to define acls and enforce them.