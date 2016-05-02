The data node maintains a file per replicated store. We call this file the on-disk log. The on-disk log is a pre-allocated file in a standard linux file system (ext4/xfs). In Ambry, we pre-allocate a file for each on-disk log. 
The basic idea for the replicated store is the following : on put, append blobs to the end of the pre-allocated file so as to encourage a sequential write workload. Any gets that are serviced by the replicated store may incur a random disk IO, but we expect good locality in the page cache. Deletes, like puts, are appended as a record at the end of the file.

To be able to service random reads of either user metadata or blobs, the replicated store must maintain an index that maps blob IDs to specific offsets in the on-disk log. We store other attributes as well in this index such as delete flags and ttl values for each blob. The index is designed as a set of sorted files. The most recent index segment is in memory. The older segments are memory mapped and an entry is located by doing a binary search on them. The search moves from the most recent to the oldest. This makes it easy to identify the deleted entry before the put entry. 

[[images/store.png]]

Each index segment also contains a bloom filter to optimize on IO. The search first looks up the bloom filter to identify if the entry is present on disk. Bloom filters are great at providing a probabilistic answer to whether the blob is present on disk or not. This probability is completely tunable at the cost of more storage. The bloom filters are read to memory on startup. The most recent segment is in memory and gets checkpointed from time to time. The log serves two purpose - it acts as the data log to store the blobs and also as the transaction log to recover the index on a crash.

The recovery of the store is done by scanning the log from the last known checkpointed offset. On reading the log, the index entries are populated that failed to be checkpointed before the crash. Also, since the delete entries in the log follow the original entries, the recovery would naturally update the blobs as deleted in the index. The recovery as restores the bloom filters if they are corrupt.

The store also has the following optimizations - 

**Zero copy for gets**
The store is designed to read the log and directly transfer the bytes to the socket during gets. This ensures that we could push bytes to the socket with lesser system calls as well as reduce the memory footprint for the java heap. This speeds up the read request and helps in the total end to end latency.

**Maximum page cache usage**
The store is designed to use as minimal memory as possible. This helps to maximize the page cache size. Some of the things we do to maximize the page cache size are
1. Do zero copy to avoid creating more buffers in the heap
2. Stream content to disk to avoid reading the whole content in memory
3. Maintaining only the most recent index in memory and memory mapping the rest of the index segments

**Using fallocate to preallocate the log**
We get pretty good write throughput since our writes are sequential. We further optimize the performance by preallocating the log when the replica is created. The partitions are of fixed size and this enables Ambry to use fallocate to create the log.