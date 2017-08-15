The clustermap controls the topology, maintains resource states and helps coordinate cluster operations. Cluster management used to be static in earlier versions of Ambry. Ambry now makes use of dynamic cluster management using [Helix](http://helix.apache.org). Both ambry frontends and ambry servers run the Helix cluster manager agent which allows for the following among other things:

1. Dynamic detection of node and disk failures which helps ensure that requests are not routed to partitions hosted on failed resources.
2. Dynamic addition and removal of resources.

Helix based cluster management also allows us to introduce features that require coordination among nodes, such as more control over replication, dynamic reallocation of resources and self healing, etc. 

Helix based cluster management design is discussed in detail [here](https://docs.google.com/document/d/1gMweKKzpgcGciXzhNpjI3gf9973QhkZoYA6YkUhaFvU/edit?usp=sharing).