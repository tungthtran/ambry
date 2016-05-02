**What use cases is Ambry suitable for?**  

Ambry was built to support real time storage and serving of Media content. This means Ambry is suitable for use cases that need high availability, low latency, high throughput, efficient for small and large objects and easy to operate. However, Ambry can also be used for other use cases as well which includes database backups, reports and search indexes.

**What is the consistency model for Ambry?**  

Ambry is eventually consistent and chooses high availability and works under network partitions. The replication in Ambry reconciles the replicas when the network partition is restored. When a partition is not available in one data center, it proxies the request to another data center for reads. For writes, it chooses a partition that is available and hence down machines and partitions do not affect the availability of writes. Ambry is also multi master design which means there is no downtime for leader elections before normal operation can resume.

**Does Ambry support encryption?**  

Ambry does support encryption between servers within and across data centers. It also supports encryption between frontends and the servers. However, encryption is still not supported between the client and the frontend. It is possible to support encryption at the client if the router library that the frontend uses can be directly embedded in the client.

**What durability guarantees does Ambry provide?**  

Ambry has tunable durability guarantees. There are knobs that can be tweaked at the routing layer as well as the storage layer. At the routing layer, Puts use a quorum protocol to ensure writes are successful. Put writes to M replicas and succeeds when N replicas return success. N is less than or equal to M. This helps to decide on the level of durability required. For example, if a partition has 3 replicas and the quorum is 2, Put will succeed when 2 replicas return success. This means that writes can survive on machine failure assuming the replicas are on different machines. At the storage layer, Ambry flushes data asynchronously to disk. This helps to ensure that writes are really fast as they write it to the page cache and return immediately. This also does not affect durability as Ambry depends on quorum at the routing layer and has replication to ensure durability. However, the flush frequency can be completely controlled. The flush frequency can be made smaller to ensure higher durability at the disk level. 

**What languages are the client library available in?**  

Ambry provides a REST frontend. This means that using Ambry is trivial across all languages. However, if clients want to embed the router library directly into their application, JAVA is the only language supported currently. We will work actively with the community to build more language support for the router library.

**What are the biggest benefits of Ambry over distributed file systems?**  

Ambry is designed to be an object store and is completely distributed. There is no single point of failure, metadata operations do not add any extra IO and Ambry is very lightweight to manage. As a bonus, the codebase is also really small which enables easy maintenance and debugging. We believe that these attributes are very unique to Ambry. Distributed file system in general tend to support many features, are very complex, have higher stronger consistency guarantees which affect performance and have a huge codebase that makes it hard to understand the internal or debug.

**Where can I read more information on Ambry?**  

Ambry has a paper published in SIGMOD 2016. More information can be found here - http://sigmod2016.org/sigmod_industrial_list.shtml
