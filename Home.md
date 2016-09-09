Media content has become ubiquitous around the web and almost all of Linkedin's new features interact with media in some form or the other. Profile photos, email attachments, logos and influencer posts are some examples where photos, videos, pdfs and other media types get uploaded and displayed to the end user. These media types get stored on our backend and are predominantly served by our CDNs. The backend storage system acts as the origin server for the content served by the CDNs.
  
Ambry is a distributed immutable blob storage system that is highly available, very easy to scale, optimized to serve immutable objects of few KBs to multiple GBs in size with high throughput and low latency and enables end to end streaming from the clients to the storage tiers and vice versa.  The system has been built to work under active-active setup across multiple datacenters and provides very cheap storage.

# Design Goals
## Highly Available and horizontally scalable
Ambry is an highly available and eventually consistent system. In most cases, writes are written to a local datacenter and asynchronous replication copies the data to the other data centers. This ensures that under network partitions, writes to the local datacenter is still available. Also, when a machine is not available locally, Ambry chooses another replica on another machine in the same datacenter to read or write the data. For reads, when data is not present in the local datacenter, it proxies request to the datacenters that has the blob. Of course, all of these are configurable.

## Low operational overhead 
A key design goal for Ambry is to make it very easy to operate the cluster. The system is completely decentralized and comes with all the necessary tooling to manage the cluster. Also, most of the operations will be automated within the software to ensure that there is very little manual effort required to maintain the cluster.

## Low MTTR (Mean time to repair)
This is so important for distributed systems. Machines go down, disks fail, servers crash and GC stalls the process. All of these failures are completely acceptable faults in a distributed system. The key however is to fix the issue in very short span of time. In all cases, the system will be available during repairs. However, it is still important to have a low MTTR. This is possible because Ambry has a very simple design and easy to debug.

## Active-Active cross DC
By default, Ambry supports active active setup. This means that objects can be written to the same partition in any datacenter and can be read from any other data center. This is usually not a common features provided by many systems. Ambry achieves this through replication and also proxying request to remote data centers when required.

## Works for large and small media objects
This is a key characteristic that is required for a media store. Most media traffic consist of trillions of small objects and billion of large objects. The system needs to function well for this mixed workload. The way this is achieved in Ambry is to combine the writes of all objects into a sequential log. This ensures that all writes are batches and flushed asynchronously and fragmentation is very less on the disk.

## Cost efficient
Finally, any object store would need to store media and data types for a long time. The older data become cold over time and has very low read QPS. Also, objects are usually large and take up a lot of space. The design should be such that it enables JBOD, supports hard disks and keeps the space amplification to a minimum.