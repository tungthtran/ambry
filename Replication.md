The replication service runs on top of the store. The store itself is agnostic to replication. On startup, a replication manager starts up some threads and allocates partitions to these threads. The thread is then responsible for replicating the partition from remote nodes. The assignment strategy of partition to threads is done in such a way to isolate data centers and increase batching. A typical replication for a replica of partition A in one machine with another replica involves the following steps - 


[[images/replication1.png]]
    




***

  
    
[[images/replication2.png]]
    
  
  

***

[[images/replication3.png]]
  
  

***

  
[[images/replication4.png]]
  
  

***

  
[[images/replication5.png]]
  
  
  
***

[[images/replication6.png]]
  
  

***

  
[[images/replication7.png]]



***
