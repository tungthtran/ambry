# Introduction
The clustermap controls the topology, maintains resource states and helps coordinate cluster operations. Ambry now has dynamic cluster management. What does that mean? Let us first look at how cluster management worked with the static cluster manager.

Previously, with the static cluster management, the cluster layout was kept in two json files:
* HardwareLayout.json which contained information about data centers, nodes, disks and their capacities;
* PartitionLayout.json which contained information about the partitions, replicas, their locations and their capacities.

The static cluster manager reads and interprets these files once - at startup - and the loaded in-memory information never changes during the lifetime of the ambry-frontend or the ambry-server processes. This means that any change including new host and partition additions, replica to host associations, updates to partitions’ sealed states, etc. requires updating these files, deploying them to the nodes and then restarting the ambry process on those nodes. Failure detection is the only dynamic aspect of the static cluster manager.

With the dynamic cluster manager backed by Helix, we get the following:
* A fault-tolerant centralized location for cluster information that is accessible to all nodes of the cluster.
* Ability for a node to dynamically update its state and other information.
* Ability for all nodes to dynamically receive updates to the cluster information made by other nodes voluntarily (when they update a replica’s sealed state, say) or involuntarily (when the node goes down).

A detailed design can be found [here](https://docs.google.com/document/d/1gMweKKzpgcGciXzhNpjI3gf9973QhkZoYA6YkUhaFvU/).

The Helix based dynamic cluster manager improves things by allowing for the following among other things:
* Dynamic failure detection: Ambry uses a combination of the old logic (where failures are detected based on failed responses to requests) and callbacks from Helix for instantly detecting when nodes are down or up.
* Dynamic cluster expansion: Ambry is now set up for cluster expansions (new node and partition additions) without requiring any restarts or deployments to existing nodes.
* Dynamic updates to sealed states of partitions and replicas: Nodes can now dynamically update the sealed states of replicas.
* Sets Ambry up for future features such as dynamic rebalancing.

# Components and Layout
Helix based cluster management requires the following in each datacenter of the cluster:
* A ZooKeeper endpoint.
* A Helix controller.

As described in the design document:
* Ambry cluster information is spread across ZK clusters in every datacenter. Each ZK cluster is used to manage the part of the Ambry cluster local to the respective datacenter. 
* Every Ambry server registers as a Participant to the local ZK end point. Every Ambry server and every Ambry frontend registers as a Spectator to local and remote ZK end points.

# Configs
The following are the newly applicable configs (unless specified otherwise, these apply to both frontends and servers):

****``clustermap.cluster.agents.factory``****: this specifies what kind of cluster manager is to be used. There are three possibilities currently:
* Helix cluster manager (default)
* Static cluster manager (deprecated)
* Composite cluster manager: a cluster manager that instantiates other kinds of cluster managers such as the static and the helix ones, and internally relays information to both. This is useful for debugging and migration.

****``clustermap.dcs.zk.connect.strings``****:this should be a serialized json containing the information about all the zk hosts that the Helix based cluster manager should be aware of. This information should be of the following form:
```
{
   "zkInfo" : [
     {
       "datacenter":"dc1",
       "zkConnectStr":"abc.example.com:2199",
     },
     {
       "datacenter":"dc2",
       "zkConnectStr":"def.example.com:2300",
     }
   ]
}
```
****``clustermap.cluster.name``****: the name of the Ambry cluster. This will be used to identify the ZK root node. The root node will be prefixed with “Ambry-” followed by the Ambry cluster name.

****``clustermap.datacenter.name``****: the datacenter in which the given node resides.

****``clustermap.host.name``****: the hostname associated with the node.

****``clustermap.port``****: the port associated with the node. When registering as a spectator or a participant, every node should have a unique “host:port” combination.

# Migration
In order to migrate from the static cluster manager to Helix, the following has to be done.

* Get the ZK endpoints for each cluster.
* Run the Helix bootstrap and upgrade tool that takes as inputs the ZK end points from a file in json format and the static cluster map files and maps the information in the ZK services. There are example files in the config directory and below is a sample run (run from the target directory). Help on the tool’s arguments can be obtained by running it without any arguments:

```
java -Dlog4j.configuration=file:../config/log4j.properties -cp ambry.jar com.github.ambry.clustermap.HelixBootstrapUpgradeTool --hardwareLayoutPath ../config/HardwareLayoutHelix.json --partitionLayoutPath ../config/PartitionLayoutHelix.json --clusterNamePrefix Ambry- --maxPartitionsInOneResource 3 --zkLayoutPath ../config/zkLayout.json
```

When run successfully, the tool will print that it was able to successfully verify the populated information.
      
* Update the ``clustermap.cluster.agents.factory`` config to use ``com.github.ambry.clustermap.HelixClusterAgentsFactory``
* Update the ``clustermap.dcs.zk.connect.strings`` config to essentially use the same contents as in the zk layout json file provided as input to the bootstrap and upgrade tool.
* Ensure that the rest of the configs - ``ambry.cluster.name``, ``clustermap.datacenter.name``, ``clustermap.host.name``, ``clustermap.port`` - have the appropriate values. (The port number for frontends can be the port at which they run, it is only used for forming unique name when registering as a spectator).

# Setting up Dev environment for Helix cluster manager
The config directory has sample files to set up local ZK services, Helix controller, ambry servers and frontends to test Helix cluster manager. The sample cluster is named "Ambry-Proto" and the layout consists of:
* 2 datacenters.
* 3 ambry server in either datacenter.
* 3 disks in each ambry server.
* 4 partitions, each with 3 replicas per datacenter distributed among the ambry servers.

To get everything running with the Helix cluster manager, do the following:

* Get Helix (if not done already) from http://helix.apache.org and follow the instructions (this will also bring in ZK). Then start ZK services (one for each DC at the port specified in the configs) as follows:
```
cd <path_to_helix>/helix/helix-core/target/helix-core-pkg/bin
./start-standalone-zookeeper.sh 2300 &
./start-standalone-zookeeper.sh 2199 &
```

* Run the bootstrap tool to map the static layout information in Helix.
```
cd <path_to_ambry>/target
java -Dlog4j.configuration=file:../config/log4j.properties -cp ambry.jar com.github.ambry.clustermap.HelixBootstrapUpgradeTool --hardwareLayoutPath ../config/HardwareLayoutHelix.json --partitionLayoutPath ../config/PartitionLayoutHelix.json --clusterNamePrefix Ambry- --maxPartitionsInOneResource 3 --zkLayoutPath ../config/zkLayout.json
```
* Run the Helix controller in each datacenter for the cluster that was created.
```
cd <path_to_helix>/helix/helix-core/target/helix-core-pkg/bin
./run-helix-controller.sh --zkSvr localhost:2199 --cluster Ambry-Proto
./run-helix-controller.sh --zkSvr localhost:2300 --cluster Ambry-Proto
```
* Create directories for the disk mount paths if they do not exist:
```
mkdir -p /tmp/{a,b,c}/{0,1,2}
```
* Start up ambry servers - all 6 of them (repeat the following for servers 2 to 6):
```
cd <path_to_ambry>/target
java -Dlog4j.configuration=file:../config/log4j.properties -cp ambry.jar com.github.ambry.server.AmbryMain --serverPropsFilePath ../config/server1_helix.properties --hardwareLayoutFilePath ../config/HardwareLayoutHelix.json --partitionLayoutFilePath ../config/PartitionLayoutHelix.json
```
* Start a frontend:
```
java -Dlog4j.configuration=file:../config/log4j.properties -cp ambry.jar com.github.ambry.frontend.AmbryFrontendMain --serverPropsFilePath ../config/frontend_helix.properties --hardwareLayoutFilePath ../config/HardwareLayoutHelix.json --partitionLayoutFilePath ../config/PartitionLayoutHelix.json
```

This sets up an Ambry cluster that uses Helix cluster manager. Server nodes can be brought down and up, and the log messages in the other nodes will show that node failures are detected.