These are the most common operational task that need to be performed on Ambry. 

### Add Partition

    java com.github.ambry.clustermap.PartitionManager --hardwareLayoutPath config/HardwareLayout.json --partitionLayoutPath config/PartitionLayout.json --operationType AddPartition --numberOfPartitionsToAdd 100 --numberOfReplicasPerDatacenter 3 --replicaCapacityInBytes 107374182400

### Add Replicas

    java com.github.ambry.clustermap.PartitionManager --hardwareLayoutPath config/HardwareLayout.json --partitionLayoutPath config/PartitionLayout.json --operationType AddReplicas --partitionIdToAddReplicasTo 2 --numberOfReplicasPerDatacenter 3 --replicaCapacityInBytes 107374182400

### Add Nodes

To add new nodes to the cluster, 

1. Add new nodes to the hardware layout. This is a very simple edit of the hardware layout file to add more nodes.
2. Use the AddPartition command and specify the number of partitions to be added to the new nodes. 

### Add New Datacenter

This is similar to adding nodes with the only difference that we would need to add the entire datacenter to the hardware layout. Once the hardwarelayout is updated, one can simply add more partitions by using the addpartition command.

### Mark Partition ReadOnly

This would require manually editing the partition layout file and specifying READ_ONLY against all the partitions that needs to be made read only.

### Move Replicas

This would require the following steps - 

1. Add the new replicas using the AddReplica command
2. Push the config to the cluster and start the servers
3. Let the replicas catch up
4. Remove the old replicas manually from the partition layout file
5. Push the file to the cluster and manually delete the files for the replica from disk

### Dump Log
    java com.github.ambry.tools.admin.DumpData --hardwareLayoutPath config/HardwareLayout.json --partitionLayoutPath config/PartitionLayout.json --typeOfOperation DumpLog --logFileToDump partition1/log1 --outFile output/log1Dump 

### Dump Index
    java com.github.ambry.tools.admin.DumData --hardwareLayoutPath config/HardwareLayout.json --partitionLayoutPath config/PartitionLayout.json --operationType DumpIndex --replicaRootDirectoy partition1/

### Dump Replica Token File
    java com.github.ambry.tools.admin.DumpData --hardwareLayoutPath config/HardwareLayout.json --partitionLayoutPath config/PartitionLayout.json --operationType DumpReplicatoken --fileToRead partition1/findToken
