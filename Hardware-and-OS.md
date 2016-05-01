### OS
Ambry should run well on any unix system and has been tested on Linux.
We have not tested Ambry on Windows

You likely don't need to do much OS-level tuning though there are a few things that will help performance.

Two configurations that may be important:

We upped the number of file descriptors since we have lots of partitions and lots of connections.
We upped the max socket buffer size to enable high-performance data transfer between data centers.

### Disks and Filesystem

We recommend using multiple drives to get good throughput and not sharing the same drives used for Ambry data with application logs or other OS filesystem activity to ensure good latency. When partitions are created, the replicas are placed such that the data is balanced across all available disks.

### Application vs. OS Flush Management

Ambry always immediately writes all data to the filesystem and supports the ability to configure the flush policy that controls when data is forced out of the OS cache and onto disk. This flush policy can be controlled to force data to disk after a period of time.

Ambry must eventually call fsync to know that data was flushed. When recovering from a crash for any log segment not known to be fsync'd Ambry will check the integrity of each message by checking its CRC and also rebuild the accompanying index file as part of the recovery process executed on startup.

Note that durability in Ambry does not require syncing data to disk, as a failed node will always recover from its replicas.

The drawback of using application level flush settings are that this is less efficient in it's disk usage pattern (it gives the OS less leeway to re-order writes) and it can introduce latency as fsync in most Linux filesystems blocks writes to the file whereas the background flushing does much more granular page-level locking. Hence, our recommendation would be to have a large checkpoint interval and let Linux page cache flush kick in.

### Understanding Linux OS Flush Behavior

In Linux, data written to the filesystem is maintained in pagecache until it must be written out to disk (due to an application-level fsync or the OS's own flush policy). The flushing of data is done by a set of background threads called pdflush (or in post 2.6.32 kernels "flusher threads").
Pdflush has a configurable policy that controls how much dirty data can be maintained in cache and for how long before it must be written back to disk. This policy is described here. When Pdflush cannot keep up with the rate of data being written it will eventually cause the writing process to block incurring latency in the writes to slow down the accumulation of data.

You can see the current state of OS memory usage by doing

  > cat /proc/meminfo  
The meaning of these values are described in the link above.
Using pagecache has several advantages over an in-process cache for storing data that will be written out to disk:

The I/O scheduler will batch together consecutive small writes into bigger physical writes which improves throughput.
The I/O scheduler will attempt to re-sequence writes to minimize movement of the disk head which improves throughput.
It automatically uses all the free memory on the machine