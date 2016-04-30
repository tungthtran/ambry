The clustermap controls the topology, maintains resource states and helps coordinate cluster operations. There are two parts to the cluster map: 

1. A hardware layout that contains the list of machines, disks in each of the machines and capacity of each of the disks. The layout also maintains the state of the resources (machines and disk) and specifies the hostname and ports (plain and SSL) that can be used to connect to the server.

1. A partition layout that contains the list of partitions, their placement information and their states. A partition in Ambry is a logical slice of the cluster. Typically, a partition has a numeric ID, a list of replicas that can span cross data center and a state that indicates if it is available for writes. Partitions are fixed size resources and any data rebalancing across the cluster happens at the partition level.

The storage servers and the frontend servers have access to the clustermap and use their current view at all times to make decisions such as choosing an available machine, filtering down replicas and identifying location of an object.
