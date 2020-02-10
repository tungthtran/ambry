/**
 * Copyright 2016 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package com.github.ambry.replication;

import com.codahale.metrics.MetricRegistry;
import com.github.ambry.clustermap.AmbryReplicaSyncUpManager;
import com.github.ambry.clustermap.ClusterMap;
import com.github.ambry.clustermap.ClusterMapChangeListener;
import com.github.ambry.clustermap.ClusterMapUtils;
import com.github.ambry.clustermap.DataNodeId;
import com.github.ambry.clustermap.MockClusterMap;
import com.github.ambry.clustermap.MockDataNodeId;
import com.github.ambry.clustermap.MockHelixParticipant;
import com.github.ambry.clustermap.MockPartitionId;
import com.github.ambry.clustermap.MockReplicaId;
import com.github.ambry.clustermap.PartitionId;
import com.github.ambry.clustermap.ReplicaId;
import com.github.ambry.clustermap.ReplicaState;
import com.github.ambry.clustermap.ReplicaSyncUpManager;
import com.github.ambry.clustermap.StateModelListenerType;
import com.github.ambry.clustermap.StateTransitionException;
import com.github.ambry.commons.BlobId;
import com.github.ambry.commons.BlobIdFactory;
import com.github.ambry.commons.CommonTestUtils;
import com.github.ambry.commons.ResponseHandler;
import com.github.ambry.config.ClusterMapConfig;
import com.github.ambry.config.DiskManagerConfig;
import com.github.ambry.config.ReplicationConfig;
import com.github.ambry.config.StoreConfig;
import com.github.ambry.config.VerifiableProperties;
import com.github.ambry.messageformat.BlobProperties;
import com.github.ambry.messageformat.DeleteMessageFormatInputStream;
import com.github.ambry.messageformat.MessageFormatException;
import com.github.ambry.messageformat.MessageFormatInputStream;
import com.github.ambry.messageformat.PutMessageFormatInputStream;
import com.github.ambry.messageformat.TtlUpdateMessageFormatInputStream;
import com.github.ambry.messageformat.ValidatingTransformer;
import com.github.ambry.network.ConnectedChannel;
import com.github.ambry.network.Port;
import com.github.ambry.network.PortType;
import com.github.ambry.protocol.ReplicaMetadataRequest;
import com.github.ambry.protocol.ReplicaMetadataResponse;
import com.github.ambry.store.Message;
import com.github.ambry.store.MessageInfo;
import com.github.ambry.store.MockId;
import com.github.ambry.store.MockMessageWriteSet;
import com.github.ambry.store.MockStoreKeyConverterFactory;
import com.github.ambry.store.StorageManager;
import com.github.ambry.store.Store;
import com.github.ambry.store.StoreKey;
import com.github.ambry.store.StoreKeyConverter;
import com.github.ambry.store.StoreKeyFactory;
import com.github.ambry.store.TransformationOutput;
import com.github.ambry.store.Transformer;
import com.github.ambry.utils.ByteBufferInputStream;
import com.github.ambry.utils.MockTime;
import com.github.ambry.utils.Pair;
import com.github.ambry.utils.SystemTime;
import com.github.ambry.utils.TestUtils;
import com.github.ambry.utils.Time;
import com.github.ambry.utils.Utils;
import com.github.ambry.utils.UtilsTest;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import static com.github.ambry.clustermap.MockClusterMap.*;
import static com.github.ambry.clustermap.StateTransitionException.TransitionErrorCode.*;
import static com.github.ambry.clustermap.TestUtils.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


/**
 * Tests for ReplicaThread for both pairs of compatible ReplicaMetadataRequest and ReplicaMetadataResponse
 * {@code ReplicaMetadataRequest#Replica_Metadata_Request_Version_V1}, {@code ReplicaMetadataResponse#REPLICA_METADATA_RESPONSE_VERSION_V_5}
 * {@code ReplicaMetadataRequest#Replica_Metadata_Request_Version_V2}, {@code ReplicaMetadataResponse#REPLICA_METADATA_RESPONSE_VERSION_V_6}
 */
@RunWith(Parameterized.class)
public class ReplicationTest {

  private static int CONSTANT_TIME_MS = 100000;
  private static long EXPIRY_TIME_MS = SystemTime.getInstance().milliseconds() + TimeUnit.DAYS.toMillis(7);
  private static long UPDATED_EXPIRY_TIME_MS = SystemTime.getInstance().milliseconds() + TimeUnit.DAYS.toMillis(14);
  private static final short VERSION_2 = 2;
  private static final short VERSION_5 = 5;
  private final MockTime time = new MockTime();
  private VerifiableProperties verifiableProperties;
  private ReplicationConfig replicationConfig;

  /**
   * Running for the two sets of compatible ReplicaMetadataRequest and ReplicaMetadataResponse,
   * viz {{@code ReplicaMetadataRequest#Replica_Metadata_Request_Version_V1}, {@code ReplicaMetadataResponse#REPLICA_METADATA_RESPONSE_VERSION_V_5}}
   * & {{@code ReplicaMetadataRequest#Replica_Metadata_Request_Version_V2}, {@code ReplicaMetadataResponse#REPLICA_METADATA_RESPONSE_VERSION_V_6}}
   * @return an array with both pairs of compatible request and response.
   */
  @Parameterized.Parameters
  public static List<Object[]> data() {
    return Arrays.asList(new Object[][]{{ReplicaMetadataRequest.Replica_Metadata_Request_Version_V1,
        ReplicaMetadataResponse.REPLICA_METADATA_RESPONSE_VERSION_V_5},
        {ReplicaMetadataRequest.Replica_Metadata_Request_Version_V2,
            ReplicaMetadataResponse.REPLICA_METADATA_RESPONSE_VERSION_V_6}});
  }

  /**
   * Constructor to set the configs
   */
  public ReplicationTest(short requestVersion, short responseVersion) throws Exception {
    List<com.github.ambry.utils.TestUtils.ZkInfo> zkInfoList = new ArrayList<>();
    zkInfoList.add(new com.github.ambry.utils.TestUtils.ZkInfo(null, "DC1", (byte) 0, 2199, false));
    JSONObject zkJson = constructZkLayoutJSON(zkInfoList);
    Properties properties = new Properties();
    properties.setProperty("replication.metadata.request.version", Short.toString(requestVersion));
    properties.setProperty("replication.metadataresponse.version", Short.toString(responseVersion));
    properties.setProperty("replication.cloud.token.factory", "com.github.ambry.replication.MockFindTokenFactory");
    properties.setProperty("clustermap.cluster.name", "test");
    properties.setProperty("clustermap.datacenter.name", "DC1");
    properties.setProperty("clustermap.host.name", "localhost");
    properties.setProperty("clustermap.dcs.zk.connect.strings", zkJson.toString(2));
    properties.setProperty("clustermap.replica.catchup.acceptable.lag.bytes", Long.toString(100L));
    properties.setProperty("replication.synced.replica.backoff.duration.ms", "3000");
    properties.setProperty("replication.intra.replica.thread.throttle.sleep.duration.ms", "100");
    properties.setProperty("replication.inter.replica.thread.throttle.sleep.duration.ms", "200");
    properties.setProperty("replication.replica.thread.idle.sleep.duration.ms", "1000");
    properties.setProperty("replication.track.per.partition.lag.from.remote", "true");
    properties.put("store.segment.size.in.bytes", Long.toString(MockReplicaId.MOCK_REPLICA_CAPACITY / 2L));
    verifiableProperties = new VerifiableProperties(properties);
    replicationConfig = new ReplicationConfig(verifiableProperties);
  }

  /**
   * Tests add/remove replicaInfo to {@link ReplicaThread}
   * @throws Exception
   */
  @Test
  public void remoteReplicaInfoAddRemoveTest() throws Exception {
    MockClusterMap clusterMap = new MockClusterMap();
    Pair<MockHost, MockHost> localAndRemoteHosts = getLocalAndRemoteHosts(clusterMap);
    MockHost localHost = localAndRemoteHosts.getFirst();
    MockHost remoteHost = localAndRemoteHosts.getSecond();
    StoreKeyFactory storeKeyFactory = Utils.getObj("com.github.ambry.commons.BlobIdFactory", clusterMap);
    MockStoreKeyConverterFactory mockStoreKeyConverterFactory = new MockStoreKeyConverterFactory(null, null);
    mockStoreKeyConverterFactory.setReturnInputIfAbsent(true);
    mockStoreKeyConverterFactory.setConversionMap(new HashMap<>());
    StoreKeyConverter storeKeyConverter = mockStoreKeyConverterFactory.getStoreKeyConverter();
    Transformer transformer = new ValidatingTransformer(storeKeyFactory, storeKeyConverter);

    ReplicationMetrics replicationMetrics =
        new ReplicationMetrics(new MetricRegistry(), clusterMap.getReplicaIds(localHost.dataNodeId));
    replicationMetrics.populateSingleColoMetrics(remoteHost.dataNodeId.getDatacenterName());
    List<RemoteReplicaInfo> remoteReplicaInfoList = localHost.getRemoteReplicaInfos(remoteHost, null);
    Map<DataNodeId, MockHost> hosts = new HashMap<>();
    hosts.put(remoteHost.dataNodeId, remoteHost);
    MockConnectionPool connectionPool = new MockConnectionPool(hosts, clusterMap, 4);
    ReplicaThread replicaThread =
        new ReplicaThread("threadtest", new MockFindTokenHelper(storeKeyFactory, replicationConfig), clusterMap,
            new AtomicInteger(0), localHost.dataNodeId, connectionPool, replicationConfig, replicationMetrics, null,
            mockStoreKeyConverterFactory.getStoreKeyConverter(), transformer, clusterMap.getMetricRegistry(), false,
            localHost.dataNodeId.getDatacenterName(), new ResponseHandler(clusterMap), time, null);
    for (RemoteReplicaInfo remoteReplicaInfo : remoteReplicaInfoList) {
      replicaThread.addRemoteReplicaInfo(remoteReplicaInfo);
    }
    List<RemoteReplicaInfo> actualRemoteReplicaInfoList =
        replicaThread.getRemoteReplicaInfos().get(remoteHost.dataNodeId);
    Comparator<RemoteReplicaInfo> remoteReplicaInfoComparator =
        Comparator.comparing(info -> info.getReplicaId().getPartitionId().toPathString());
    Collections.sort(remoteReplicaInfoList, remoteReplicaInfoComparator);
    Collections.sort(actualRemoteReplicaInfoList, remoteReplicaInfoComparator);
    assertEquals("getRemoteReplicaInfos not correct", remoteReplicaInfoList, actualRemoteReplicaInfoList);

    // Test remove remoteReplicaInfo.
    replicaThread.removeRemoteReplicaInfo(remoteReplicaInfoList.get(remoteReplicaInfoList.size() - 1));
    actualRemoteReplicaInfoList = replicaThread.getRemoteReplicaInfos().get(remoteHost.dataNodeId);
    Collections.sort(actualRemoteReplicaInfoList, remoteReplicaInfoComparator);
    remoteReplicaInfoList.remove(remoteReplicaInfoList.size() - 1);
    assertEquals("getRemoteReplicaInfos not correct", remoteReplicaInfoList, actualRemoteReplicaInfoList);
  }

  /**
   * Test dynamically add/remove replica in {@link ReplicationManager}
   * @throws Exception
   */
  @Test
  public void addAndRemoveReplicaTest() throws Exception {
    MockClusterMap clusterMap = new MockClusterMap();
    ClusterMapConfig clusterMapConfig = new ClusterMapConfig(verifiableProperties);
    StoreConfig storeConfig = new StoreConfig(verifiableProperties);
    DataNodeId dataNodeId = clusterMap.getDataNodeIds().get(0);
    MockStoreKeyConverterFactory storeKeyConverterFactory = new MockStoreKeyConverterFactory(null, null);
    storeKeyConverterFactory.setConversionMap(new HashMap<>());
    StorageManager storageManager =
        new StorageManager(storeConfig, new DiskManagerConfig(verifiableProperties), Utils.newScheduler(1, true),
            new MetricRegistry(), null, clusterMap, dataNodeId, null, null, new MockTime(), null);
    storageManager.start();
    MockReplicationManager replicationManager =
        new MockReplicationManager(replicationConfig, clusterMapConfig, storeConfig, storageManager, clusterMap,
            dataNodeId, storeKeyConverterFactory, null);
    ReplicaId replicaToTest = clusterMap.getReplicaIds(dataNodeId).get(0);
    // Attempting to add replica that already exists should fail
    assertFalse("Adding an existing replica should fail", replicationManager.addReplica(replicaToTest));
    // Create a brand new replica that sits on one of the disk of datanode, add it into replication manager
    PartitionId newPartition = clusterMap.createNewPartition(clusterMap.getDataNodes());
    for (ReplicaId replicaId : newPartition.getReplicaIds()) {
      if (replicaId.getDataNodeId() == dataNodeId) {
        replicaToTest = replicaId;
        break;
      }
    }
    // Before adding replica, partitionToPartitionInfo and mountPathToPartitionInfos should not contain new partition
    assertFalse("partitionToPartitionInfo should not contain new partition",
        replicationManager.getPartitionToPartitionInfoMap().containsKey(newPartition));
    for (PartitionInfo partitionInfo : replicationManager.getMountPathToPartitionInfosMap()
        .get(replicaToTest.getMountPath())) {
      assertNotSame("mountPathToPartitionInfos should not contain new partition", partitionInfo.getPartitionId(),
          newPartition);
    }
    // Add new replica to replication manager
    assertTrue("Adding new replica to replication manager should succeed",
        replicationManager.addReplica(replicaToTest));
    // After adding replica, partitionToPartitionInfo and mountPathToPartitionInfos should contain new partition
    assertTrue("partitionToPartitionInfo should contain new partition",
        replicationManager.getPartitionToPartitionInfoMap().containsKey(newPartition));
    Optional<PartitionInfo> newPartitionInfo = replicationManager.getMountPathToPartitionInfosMap()
        .get(replicaToTest.getMountPath())
        .stream()
        .filter(partitionInfo -> partitionInfo.getPartitionId() == newPartition)
        .findAny();
    assertTrue("mountPathToPartitionInfos should contain new partition info", newPartitionInfo.isPresent());
    // Verify that all remoteReplicaInfos of new added replica have assigned thread
    for (RemoteReplicaInfo remoteReplicaInfo : newPartitionInfo.get().getRemoteReplicaInfos()) {
      assertNotNull("The remote replica should be assigned to one replica thread",
          remoteReplicaInfo.getReplicaThread());
    }

    // Remove replica
    assertTrue("Remove replica from replication manager should succeed",
        replicationManager.removeReplica(replicaToTest));
    // Verify replica is removed, so partitionToPartitionInfo and mountPathToPartitionInfos should not contain new partition
    assertFalse("partitionToPartitionInfo should not contain new partition",
        replicationManager.getPartitionToPartitionInfoMap().containsKey(newPartition));
    for (PartitionInfo partitionInfo : replicationManager.getMountPathToPartitionInfosMap()
        .get(replicaToTest.getMountPath())) {
      assertNotSame("mountPathToPartitionInfos should not contain new partition", partitionInfo.getPartitionId(),
          newPartition);
    }
    // Verify that none of remoteReplicaInfo should have assigned thread
    for (RemoteReplicaInfo remoteReplicaInfo : newPartitionInfo.get().getRemoteReplicaInfos()) {
      assertNull("The remote replica should be assigned to one replica thread", remoteReplicaInfo.getReplicaThread());
    }
    // Remove the same replica that doesn't exist should be no-op
    ReplicationManager mockManager = Mockito.spy(replicationManager);
    assertFalse("Remove non-existent replica should return false", replicationManager.removeReplica(replicaToTest));
    verify(mockManager, never()).removeRemoteReplicaInfoFromReplicaThread(anyList());
    storageManager.shutdown();
  }

  /**
   * Test cluster map change callback in {@link ReplicationManager} when any remote replicas are added or removed.
   * Test setup: attempt to add 3 replicas and remove 3 replicas respectively. The three replicas are picked as follows:
   *   (1) 1st replica on current node (should skip)
   *   (2) 2nd replica on remote node sharing partition with current one (should be added or removed)
   *   (3) 3rd replica on remote node but doesn't share partition with current one (should skip)
   * @throws Exception
   */
  @Test
  public void onReplicaAddedOrRemovedCallbackTest() throws Exception {
    MockClusterMap clusterMap = new MockClusterMap();
    ClusterMapConfig clusterMapConfig = new ClusterMapConfig(verifiableProperties);
    StoreConfig storeConfig = new StoreConfig(verifiableProperties);
    // pick a node with no special partition as current node
    Set<DataNodeId> specialPartitionNodes = clusterMap.getSpecialPartition()
        .getReplicaIds()
        .stream()
        .map(ReplicaId::getDataNodeId)
        .collect(Collectors.toSet());
    DataNodeId currentNode =
        clusterMap.getDataNodes().stream().filter(d -> !specialPartitionNodes.contains(d)).findFirst().get();
    MockStoreKeyConverterFactory storeKeyConverterFactory = new MockStoreKeyConverterFactory(null, null);
    storeKeyConverterFactory.setConversionMap(new HashMap<>());
    StorageManager storageManager =
        new StorageManager(storeConfig, new DiskManagerConfig(verifiableProperties), Utils.newScheduler(1, true),
            new MetricRegistry(), null, clusterMap, currentNode, null, null, new MockTime(), null);
    storageManager.start();
    MockReplicationManager replicationManager =
        new MockReplicationManager(replicationConfig, clusterMapConfig, storeConfig, storageManager, clusterMap,
            currentNode, storeKeyConverterFactory, null);
    ClusterMapChangeListener clusterMapChangeListener = clusterMap.getClusterMapChangeListener();
    // find the special partition (not on current node) and get an irrelevant replica from it
    PartitionId absentPartition = clusterMap.getSpecialPartition();
    ReplicaId irrelevantReplica = absentPartition.getReplicaIds().get(0);
    // find an existing replica on current node and one of its peer replicas on remote node
    ReplicaId existingReplica = clusterMap.getReplicaIds(currentNode).get(0);
    ReplicaId peerReplicaToRemove =
        existingReplica.getPartitionId().getReplicaIds().stream().filter(r -> r != existingReplica).findFirst().get();
    // create a new node and place a peer of existing replica on it.
    MockDataNodeId remoteNode = createDataNode(
        getListOfPorts(PLAIN_TEXT_PORT_START_NUMBER + 10, SSL_PORT_START_NUMBER + 10, HTTP2_PORT_START_NUMBER + 10),
        clusterMap.getDatacenterName((byte) 0), 3);
    ReplicaId addedReplica =
        new MockReplicaId(remoteNode.getPort(), (MockPartitionId) existingReplica.getPartitionId(), remoteNode, 0);
    // populate added replica and removed replica lists
    List<ReplicaId> replicasToAdd = new ArrayList<>(Arrays.asList(existingReplica, addedReplica, irrelevantReplica));
    List<ReplicaId> replicasToRemove =
        new ArrayList<>(Arrays.asList(existingReplica, peerReplicaToRemove, irrelevantReplica));
    PartitionInfo partitionInfo =
        replicationManager.getPartitionToPartitionInfoMap().get(existingReplica.getPartitionId());
    assertNotNull("PartitionInfo is not found", partitionInfo);
    RemoteReplicaInfo peerReplicaInfo = partitionInfo.getRemoteReplicaInfos()
        .stream()
        .filter(info -> info.getReplicaId() == peerReplicaToRemove)
        .findFirst()
        .get();
    // get the replica-thread for this peer replica
    ReplicaThread peerReplicaThread = peerReplicaInfo.getReplicaThread();

    // Test Case 1: replication manager encountered exception during startup (remote replica addition/removal will be skipped)
    replicationManager.startWithException();
    clusterMapChangeListener.onReplicaAddedOrRemoved(replicasToAdd, replicasToRemove);
    // verify that PartitionInfo stays unchanged
    verifyRemoteReplicaInfo(partitionInfo, addedReplica, false);
    verifyRemoteReplicaInfo(partitionInfo, peerReplicaToRemove, true);

    // Test Case 2: startup latch is interrupted
    CountDownLatch initialLatch = replicationManager.startupLatch;
    CountDownLatch mockLatch = Mockito.mock(CountDownLatch.class);
    doThrow(new InterruptedException()).when(mockLatch).await();
    replicationManager.startupLatch = mockLatch;
    try {
      clusterMapChangeListener.onReplicaAddedOrRemoved(replicasToAdd, replicasToRemove);
      fail("should fail because startup latch is interrupted");
    } catch (IllegalStateException e) {
      // expected
    }
    replicationManager.startupLatch = initialLatch;

    // Test Case 3: replication manager is successfully started
    replicationManager.start();
    clusterMapChangeListener.onReplicaAddedOrRemoved(replicasToAdd, replicasToRemove);
    // verify that PartitionInfo has latest remote replica infos
    verifyRemoteReplicaInfo(partitionInfo, addedReplica, true);
    verifyRemoteReplicaInfo(partitionInfo, peerReplicaToRemove, false);
    verifyRemoteReplicaInfo(partitionInfo, irrelevantReplica, false);
    // verify new added replica is assigned to a certain thread
    ReplicaThread replicaThread =
        replicationManager.getDataNodeIdToReplicaThreadMap().get(addedReplica.getDataNodeId());
    assertNotNull("There is no ReplicaThread assocated with new replica", replicaThread);
    Optional<RemoteReplicaInfo> findResult = replicaThread.getRemoteReplicaInfos()
        .get(remoteNode)
        .stream()
        .filter(info -> info.getReplicaId() == addedReplica)
        .findAny();
    assertTrue("New added remote replica info should exist in corresponding thread", findResult.isPresent());

    // verify the removed replica info's thread is null
    assertNull("Thread in removed replica info should be null", peerReplicaInfo.getReplicaThread());
    findResult = peerReplicaThread.getRemoteReplicaInfos()
        .get(peerReplicaToRemove.getDataNodeId())
        .stream()
        .filter(info -> info.getReplicaId() == peerReplicaToRemove)
        .findAny();
    assertFalse("Previous replica thread should not contain RemoteReplicaInfo that is already removed",
        findResult.isPresent());
    storageManager.shutdown();
  }

  /**
   * Test that state transition in replication manager from OFFLINE to BOOTSTRAP
   * @throws Exception
   */
  @Test
  public void replicaFromOfflineToBootstrapTest() throws Exception {
    MockClusterMap clusterMap = new MockClusterMap();
    ClusterMapConfig clusterMapConfig = new ClusterMapConfig(verifiableProperties);
    MockHelixParticipant mockHelixParticipant = new MockHelixParticipant(clusterMapConfig);
    DataNodeId currentNode = clusterMap.getDataNodeIds().get(0);
    Pair<StorageManager, ReplicationManager> managers =
        createStorageManagerAndReplicationManager(clusterMap, clusterMapConfig, mockHelixParticipant);
    StorageManager storageManager = managers.getFirst();
    MockReplicationManager replicationManager = (MockReplicationManager) managers.getSecond();
    assertTrue("State change listener in cluster participant should contain replication manager listener",
        mockHelixParticipant.getPartitionStateChangeListeners()
            .containsKey(StateModelListenerType.ReplicationManagerListener));
    // 1. test partition not found case (should throw exception)
    try {
      mockHelixParticipant.onPartitionBecomeBootstrapFromOffline("-1");
      fail("should fail because replica is not found");
    } catch (StateTransitionException e) {
      assertEquals("Transition error doesn't match", ReplicaNotFound, e.getErrorCode());
    }
    // 2. create a new partition and test replica addition success case
    ReplicaId newReplicaToAdd = getNewReplicaToAdd(clusterMap);
    PartitionId newPartition = newReplicaToAdd.getPartitionId();
    assertTrue("Adding new replica to Storage Manager should succeed", storageManager.addBlobStore(newReplicaToAdd));
    assertFalse("partitionToPartitionInfo should not contain new partition",
        replicationManager.partitionToPartitionInfo.containsKey(newPartition));
    mockHelixParticipant.onPartitionBecomeBootstrapFromOffline(newPartition.toPathString());
    assertTrue("partitionToPartitionInfo should contain new partition",
        replicationManager.partitionToPartitionInfo.containsKey(newPartition));
    // 3. test replica addition failure case
    replicationManager.partitionToPartitionInfo.remove(newPartition);
    replicationManager.addReplicaReturnVal = false;
    try {
      mockHelixParticipant.onPartitionBecomeBootstrapFromOffline(newPartition.toPathString());
      fail("should fail due to replica addition failure");
    } catch (StateTransitionException e) {
      assertEquals("Transition error doesn't match", ReplicaOperationFailure, e.getErrorCode());
    }
    replicationManager.addReplicaReturnVal = null;
    // 4. test OFFLINE -> BOOTSTRAP on existing replica (should be no-op)
    ReplicaId existingReplica = clusterMap.getReplicaIds(currentNode).get(0);
    assertTrue("partitionToPartitionInfo should contain existing partition",
        replicationManager.partitionToPartitionInfo.containsKey(existingReplica.getPartitionId()));
    mockHelixParticipant.onPartitionBecomeBootstrapFromOffline(existingReplica.getPartitionId().toPathString());
    storageManager.shutdown();
  }

  /**
   * Test BOOTSTRAP -> STANDBY transition on both existing and new replicas. For new replica, we test both failure and
   * success cases.
   * @throws Exception
   */
  @Test
  public void replicaFromBootstrapToStandbyTest() throws Exception {
    MockClusterMap clusterMap = new MockClusterMap();
    ClusterMapConfig clusterMapConfig = new ClusterMapConfig(verifiableProperties);
    MockHelixParticipant mockHelixParticipant = new MockHelixParticipant(clusterMapConfig);
    Pair<StorageManager, ReplicationManager> managers =
        createStorageManagerAndReplicationManager(clusterMap, clusterMapConfig, mockHelixParticipant);
    StorageManager storageManager = managers.getFirst();
    MockReplicationManager replicationManager = (MockReplicationManager) managers.getSecond();
    // 1. test existing partition trough Bootstrap-To-Standby transition, should be no op.
    PartitionId existingPartition = replicationManager.partitionToPartitionInfo.keySet().iterator().next();
    mockHelixParticipant.onPartitionBecomeStandbyFromBootstrap(existingPartition.toPathString());
    assertEquals("Store state doesn't match", ReplicaState.STANDBY,
        storageManager.getStore(existingPartition).getCurrentState());
    // 2. test transition failure due to store not started
    storageManager.shutdownBlobStore(existingPartition);
    try {
      mockHelixParticipant.onPartitionBecomeStandbyFromBootstrap(existingPartition.toPathString());
      fail("should fail because store is not started");
    } catch (StateTransitionException e) {
      assertEquals("Error code doesn't match", StoreNotStarted, e.getErrorCode());
    }

    // 3. create new replica and add it into storage manager, test replica that needs to initiate bootstrap
    ReplicaId newReplicaToAdd = getNewReplicaToAdd(clusterMap);
    assertTrue("Adding new replica to Storage Manager should succeed", storageManager.addBlobStore(newReplicaToAdd));
    // override partition state change listener in ReplicationManager to help thread manipulation
    mockHelixParticipant.registerPartitionStateChangeListener(StateModelListenerType.ReplicationManagerListener,
        replicationManager.replicationListener);
    CountDownLatch participantLatch = new CountDownLatch(1);
    replicationManager.listenerExecutionLatch = new CountDownLatch(1);
    // create a new thread and trigger BOOTSTRAP -> STANDBY transition
    Utils.newThread(() -> {
      mockHelixParticipant.onPartitionBecomeStandbyFromBootstrap(newReplicaToAdd.getPartitionId().toPathString());
      participantLatch.countDown();
    }, false).start();
    assertTrue("Partition state change listener in ReplicationManager didn't get called within 1 sec",
        replicationManager.listenerExecutionLatch.await(1, TimeUnit.SECONDS));
    assertEquals("Replica should be in BOOTSTRAP state before bootstrap is complete", ReplicaState.BOOTSTRAP,
        storageManager.getStore(newReplicaToAdd.getPartitionId()).getCurrentState());
    // make bootstrap succeed
    mockHelixParticipant.getReplicaSyncUpManager().onBootstrapComplete(newReplicaToAdd);
    assertTrue("Bootstrap-To-Standby transition didn't complete within 1 sec",
        participantLatch.await(1, TimeUnit.SECONDS));
    storageManager.shutdown();
  }

  /**
   * Test STANDBY -> INACTIVE transition on existing replica (both success and failure cases)
   */
  @Test
  public void replicaFromStandbyToInactiveTest() throws Exception {
    MockClusterMap clusterMap = new MockClusterMap();
    ClusterMapConfig clusterMapConfig = new ClusterMapConfig(verifiableProperties);
    MockHelixParticipant mockHelixParticipant = new MockHelixParticipant(clusterMapConfig);
    Pair<StorageManager, ReplicationManager> managers =
        createStorageManagerAndReplicationManager(clusterMap, clusterMapConfig, mockHelixParticipant);
    StorageManager storageManager = managers.getFirst();
    MockReplicationManager replicationManager = (MockReplicationManager) managers.getSecond();
    // get an existing partition to test both success and failure cases
    PartitionId existingPartition = replicationManager.partitionToPartitionInfo.keySet().iterator().next();
    storageManager.shutdownBlobStore(existingPartition);
    try {
      mockHelixParticipant.onPartitionBecomeInactiveFromStandby(existingPartition.toPathString());
      fail("should fail because store is not started");
    } catch (StateTransitionException e) {
      assertEquals("Error code doesn't match", StoreNotStarted, e.getErrorCode());
    }
    // restart the store and trigger Standby-To-Inactive transition again
    storageManager.startBlobStore(existingPartition);

    // write a blob with size = 100 into local store (end offset of last PUT = 100 + 18 = 118)
    Store localStore = storageManager.getStore(existingPartition);
    MockId id = new MockId(UtilsTest.getRandomString(10), Utils.getRandomShort(TestUtils.RANDOM),
        Utils.getRandomShort(TestUtils.RANDOM));
    long crc = (new Random()).nextLong();
    long blobSize = 100;
    MessageInfo info =
        new MessageInfo(id, blobSize, false, false, Utils.Infinite_Time, crc, id.getAccountId(), id.getContainerId(),
            Utils.Infinite_Time);
    List<MessageInfo> infos = new ArrayList<>();
    List<ByteBuffer> buffers = new ArrayList<>();
    ByteBuffer buffer = ByteBuffer.wrap(TestUtils.getRandomBytes((int) blobSize));
    infos.add(info);
    buffers.add(buffer);
    localStore.put(new MockMessageWriteSet(infos, buffers));
    ReplicaId localReplica = storageManager.getReplica(existingPartition.toPathString());

    // override partition state change listener in ReplicationManager to help thread manipulation
    mockHelixParticipant.registerPartitionStateChangeListener(StateModelListenerType.ReplicationManagerListener,
        replicationManager.replicationListener);
    CountDownLatch participantLatch = new CountDownLatch(1);
    replicationManager.listenerExecutionLatch = new CountDownLatch(1);
    // create a new thread and trigger STANDBY -> INACTIVE transition
    Utils.newThread(() -> {
      mockHelixParticipant.onPartitionBecomeInactiveFromStandby(existingPartition.toPathString());
      participantLatch.countDown();
    }, false).start();
    assertTrue("Partition state change listener didn't get called within 1 sec",
        replicationManager.listenerExecutionLatch.await(1, TimeUnit.SECONDS));
    assertEquals("Local store state should be INACTIVE", ReplicaState.INACTIVE,
        storageManager.getStore(existingPartition).getCurrentState());

    List<RemoteReplicaInfo> remoteReplicaInfos =
        replicationManager.partitionToPartitionInfo.get(existingPartition).getRemoteReplicaInfos();
    ReplicaId peerReplica1 = remoteReplicaInfos.get(0).getReplicaId();
    // we purposely update lag to verify that local replica is present in ReplicaSyncUpManager.
    assertTrue("Updating lag between local replica and peer replica should succeed",
        mockHelixParticipant.getReplicaSyncUpManager().updateLagBetweenReplicas(localReplica, peerReplica1, 10L));
    // pick another remote replica to update the replication lag
    ReplicaId peerReplica2 = remoteReplicaInfos.get(1).getReplicaId();
    replicationManager.updateTotalBytesReadByRemoteReplica(existingPartition,
        peerReplica1.getDataNodeId().getHostname(), peerReplica1.getReplicaPath(), 118);
    assertFalse("Sync up shouldn't complete because only one replica has caught up with local replica",
        mockHelixParticipant.getReplicaSyncUpManager().isSyncUpComplete(localReplica));
    // make second peer replica catch up with last PUT in local store
    replicationManager.updateTotalBytesReadByRemoteReplica(existingPartition,
        peerReplica2.getDataNodeId().getHostname(), peerReplica2.getReplicaPath(), 118);

    assertTrue("Standby-To-Inactive transition didn't complete within 1 sec",
        participantLatch.await(1, TimeUnit.SECONDS));

    // we purposely update lag against local replica to verify local replica is no longer in ReplicaSyncUpManager because
    // deactivation is complete and local replica should be removed from "replicaToLagInfos" map.
    assertFalse("Sync up should complete (2 replicas have caught up), hence updated should be false",
        mockHelixParticipant.getReplicaSyncUpManager().updateLagBetweenReplicas(localReplica, peerReplica2, 0L));
    storageManager.shutdown();
  }

  /**
   * Test state transition in replication manager from STANDBY to LEADER (right now it is no-op in prod code, but we
   * keep test here for future use)
   * @throws Exception
   */
  @Test
  public void replicaFromStandbyToLeaderTest() throws Exception {
    MockClusterMap clusterMap = new MockClusterMap();
    ClusterMapConfig clusterMapConfig = new ClusterMapConfig(verifiableProperties);
    MockHelixParticipant mockHelixParticipant = new MockHelixParticipant(clusterMapConfig);
    Pair<StorageManager, ReplicationManager> managers =
        createStorageManagerAndReplicationManager(clusterMap, clusterMapConfig, mockHelixParticipant);
    StorageManager storageManager = managers.getFirst();
    MockReplicationManager replicationManager = (MockReplicationManager) managers.getSecond();
    PartitionId existingPartition = replicationManager.partitionToPartitionInfo.keySet().iterator().next();
    mockHelixParticipant.onPartitionBecomeLeaderFromStandby(existingPartition.toPathString());
    storageManager.shutdown();
  }

  /**
   * Test state transition in replication manager from LEADER to STANDBY (right now it is no-op in prod code, but we
   * keep test here for future use)
   * @throws Exception
   */
  @Test
  public void replicaFromLeaderToStandbyTest() throws Exception {
    MockClusterMap clusterMap = new MockClusterMap();
    ClusterMapConfig clusterMapConfig = new ClusterMapConfig(verifiableProperties);
    MockHelixParticipant mockHelixParticipant = new MockHelixParticipant(clusterMapConfig);
    Pair<StorageManager, ReplicationManager> managers =
        createStorageManagerAndReplicationManager(clusterMap, clusterMapConfig, mockHelixParticipant);
    StorageManager storageManager = managers.getFirst();
    MockReplicationManager replicationManager = (MockReplicationManager) managers.getSecond();
    PartitionId existingPartition = replicationManager.partitionToPartitionInfo.keySet().iterator().next();
    mockHelixParticipant.onPartitionBecomeStandbyFromLeader(existingPartition.toPathString());
    storageManager.shutdown();
  }

  /**
   * Test INACTIVE -> OFFLINE transition on existing replica (both success and failure cases)
   */
  @Test
  public void replicaFromInactiveToOfflineTest() throws Exception {
    MockClusterMap clusterMap = new MockClusterMap();
    ClusterMapConfig clusterMapConfig = new ClusterMapConfig(verifiableProperties);
    MockHelixParticipant mockHelixParticipant = new MockHelixParticipant(clusterMapConfig);
    Pair<StorageManager, ReplicationManager> managers =
        createStorageManagerAndReplicationManager(clusterMap, clusterMapConfig, mockHelixParticipant);
    StorageManager storageManager = managers.getFirst();
    MockReplicationManager replicationManager = (MockReplicationManager) managers.getSecond();
    // 1. test replica not found case
    try {
      mockHelixParticipant.onPartitionBecomeOfflineFromInactive("-1");
      fail("should fail because of invalid partition");
    } catch (StateTransitionException e) {
      assertEquals("Error code doesn't match", ReplicaNotFound, e.getErrorCode());
    }
    // 2. test store not started case
    PartitionId existingPartition = replicationManager.partitionToPartitionInfo.keySet().iterator().next();
    storageManager.shutdownBlobStore(existingPartition);
    try {
      mockHelixParticipant.onPartitionBecomeOfflineFromInactive(existingPartition.toPathString());
      fail("should fail because store is not started");
    } catch (StateTransitionException e) {
      assertEquals("Error code doesn't match", StoreNotStarted, e.getErrorCode());
    }
    storageManager.startBlobStore(existingPartition);
    // before testing success case, let's write a blob (size = 100) into local store and add a delete record for new blob
    Store localStore = storageManager.getStore(existingPartition);
    MockId id = new MockId(UtilsTest.getRandomString(10), Utils.getRandomShort(TestUtils.RANDOM),
        Utils.getRandomShort(TestUtils.RANDOM));
    long crc = (new Random()).nextLong();
    long blobSize = 100;
    MessageInfo info =
        new MessageInfo(id, blobSize, false, false, Utils.Infinite_Time, crc, id.getAccountId(), id.getContainerId(),
            Utils.Infinite_Time);
    List<MessageInfo> infos = new ArrayList<>();
    List<ByteBuffer> buffers = new ArrayList<>();
    ByteBuffer buffer = ByteBuffer.wrap(TestUtils.getRandomBytes((int) blobSize));
    infos.add(info);
    buffers.add(buffer);
    localStore.put(new MockMessageWriteSet(infos, buffers));
    // delete the blob
    int deleteRecordSize = 29;
    MessageInfo deleteInfo =
        new MessageInfo(id, deleteRecordSize, id.getAccountId(), id.getContainerId(), time.milliseconds());
    ByteBuffer deleteBuffer = ByteBuffer.wrap(TestUtils.getRandomBytes(deleteRecordSize));
    localStore.delete(
        new MockMessageWriteSet(Collections.singletonList(deleteInfo), Collections.singletonList(deleteBuffer)));
    // note that end offset of last PUT = 100 + 18 = 118, end offset of the store is 147 (118 + 29)
    // 3. test success case (create a new thread and trigger INACTIVE -> OFFLINE transition)
    ReplicaId localReplica = storageManager.getReplica(existingPartition.toPathString());
    // put a decommission-in-progress file into local store dir
    File decommissionFile = new File(localReplica.getReplicaPath(), "decommission_in_progress");
    assertTrue("Couldn't create decommission file in local store", decommissionFile.createNewFile());
    decommissionFile.deleteOnExit();
    assertNotSame("Before disconnection, the local store state shouldn't be OFFLINE", ReplicaState.OFFLINE,
        localStore.getCurrentState());
    mockHelixParticipant.registerPartitionStateChangeListener(StateModelListenerType.ReplicationManagerListener,
        replicationManager.replicationListener);
    CountDownLatch participantLatch = new CountDownLatch(1);
    replicationManager.listenerExecutionLatch = new CountDownLatch(1);
    Utils.newThread(() -> {
      mockHelixParticipant.onPartitionBecomeOfflineFromInactive(existingPartition.toPathString());
      participantLatch.countDown();
    }, false).start();
    assertTrue("Partition state change listener in ReplicationManager didn't get called within 1 sec",
        replicationManager.listenerExecutionLatch.await(1, TimeUnit.SECONDS));
    // the state of local store should be updated to OFFLINE
    assertEquals("Local store state is not expected", ReplicaState.OFFLINE, localStore.getCurrentState());
    // update replication lag between local and peer replicas
    List<RemoteReplicaInfo> remoteReplicaInfos =
        replicationManager.partitionToPartitionInfo.get(existingPartition).getRemoteReplicaInfos();
    ReplicaId peerReplica1 = remoteReplicaInfos.get(0).getReplicaId();
    ReplicaId peerReplica2 = remoteReplicaInfos.get(1).getReplicaId();
    // peer1 catches up with last PUT, peer2 catches up with end offset of local store. In this case, SyncUp is not complete
    replicationManager.updateTotalBytesReadByRemoteReplica(existingPartition,
        peerReplica1.getDataNodeId().getHostname(), peerReplica1.getReplicaPath(), 118);
    replicationManager.updateTotalBytesReadByRemoteReplica(existingPartition,
        peerReplica2.getDataNodeId().getHostname(), peerReplica2.getReplicaPath(), 147);
    assertFalse("Only one peer replica has fully caught up with end offset so sync-up should not complete",
        mockHelixParticipant.getReplicaSyncUpManager().isSyncUpComplete(localReplica));
    // make peer1 catch up with end offset
    replicationManager.updateTotalBytesReadByRemoteReplica(existingPartition,
        peerReplica1.getDataNodeId().getHostname(), peerReplica1.getReplicaPath(), 147);
    // Now, sync-up should complete and transition should be able to proceed.
    assertTrue("Inactive-To-Offline transition didn't complete within 1 sec",
        participantLatch.await(1, TimeUnit.SECONDS));
    assertFalse("Local store should be stopped after transition", localStore.isStarted());
    storageManager.shutdown();
  }

  /**
   * Tests pausing all partitions and makes sure that the replica thread pauses. Also tests that it resumes when one
   * eligible partition is reenabled and that replication completes successfully.
   * @throws Exception
   */
  @Test
  public void replicationAllPauseTest() throws Exception {
    MockClusterMap clusterMap = new MockClusterMap();
    Pair<MockHost, MockHost> localAndRemoteHosts = getLocalAndRemoteHosts(clusterMap);
    MockHost localHost = localAndRemoteHosts.getFirst();
    MockHost remoteHost = localAndRemoteHosts.getSecond();

    List<PartitionId> partitionIds = clusterMap.getAllPartitionIds(null);
    for (PartitionId partitionId : partitionIds) {
      // add  10 messages to the remote host only
      addPutMessagesToReplicasOfPartition(partitionId, Collections.singletonList(remoteHost), 10);
    }

    StoreKeyFactory storeKeyFactory = Utils.getObj("com.github.ambry.commons.BlobIdFactory", clusterMap);
    MockStoreKeyConverterFactory mockStoreKeyConverterFactory = new MockStoreKeyConverterFactory(null, null);
    mockStoreKeyConverterFactory.setReturnInputIfAbsent(true);
    mockStoreKeyConverterFactory.setConversionMap(new HashMap<>());

    int batchSize = 4;
    StoreKeyConverter storeKeyConverter = mockStoreKeyConverterFactory.getStoreKeyConverter();
    Transformer transformer = new ValidatingTransformer(storeKeyFactory, storeKeyConverter);
    CountDownLatch readyToPause = new CountDownLatch(1);
    CountDownLatch readyToProceed = new CountDownLatch(1);
    AtomicReference<CountDownLatch> reachedLimitLatch = new AtomicReference<>(new CountDownLatch(1));
    AtomicReference<Exception> exception = new AtomicReference<>();
    Pair<Map<DataNodeId, List<RemoteReplicaInfo>>, ReplicaThread> replicasAndThread =
        getRemoteReplicasAndReplicaThread(batchSize, clusterMap, localHost, remoteHost, storeKeyConverter, transformer,
            (store, messageInfos) -> {
              try {
                readyToPause.countDown();
                readyToProceed.await();
                if (store.messageInfos.size() == remoteHost.infosByPartition.get(store.id).size()) {
                  reachedLimitLatch.get().countDown();
                }
              } catch (Exception e) {
                exception.set(e);
              }
            }, null);
    ReplicaThread replicaThread = replicasAndThread.getSecond();
    Thread thread = Utils.newThread(replicaThread, false);
    thread.start();

    assertEquals("There should be no disabled partitions", 0, replicaThread.getReplicationDisabledPartitions().size());
    // wait to pause replication
    readyToPause.await(10, TimeUnit.SECONDS);
    replicaThread.controlReplicationForPartitions(clusterMap.getAllPartitionIds(null), false);
    Set<PartitionId> expectedPaused = new HashSet<>(clusterMap.getAllPartitionIds(null));
    assertEquals("Disabled partitions sets do not match", expectedPaused,
        replicaThread.getReplicationDisabledPartitions());
    // signal the replica thread to move forward
    readyToProceed.countDown();
    // wait for the thread to go into waiting state
    assertTrue("Replica thread did not go into waiting state",
        TestUtils.waitUntilExpectedState(thread, Thread.State.WAITING, 10000));
    // unpause one partition
    replicaThread.controlReplicationForPartitions(Collections.singletonList(partitionIds.get(0)), true);
    expectedPaused.remove(partitionIds.get(0));
    assertEquals("Disabled partitions sets do not match", expectedPaused,
        replicaThread.getReplicationDisabledPartitions());
    // wait for it to catch up
    reachedLimitLatch.get().await(10, TimeUnit.SECONDS);
    // reset limit
    reachedLimitLatch.set(new CountDownLatch(partitionIds.size() - 1));
    // unpause all partitions
    replicaThread.controlReplicationForPartitions(clusterMap.getAllPartitionIds(null), true);
    assertEquals("There should be no disabled partitions", 0, replicaThread.getReplicationDisabledPartitions().size());
    // wait until all catch up
    reachedLimitLatch.get().await(10, TimeUnit.SECONDS);
    // shutdown
    replicaThread.shutdown();
    if (exception.get() != null) {
      throw exception.get();
    }
    Map<PartitionId, List<MessageInfo>> missingInfos = remoteHost.getMissingInfos(localHost.infosByPartition);
    for (Map.Entry<PartitionId, List<MessageInfo>> entry : missingInfos.entrySet()) {
      assertEquals("No infos should be missing", 0, entry.getValue().size());
    }
    Map<PartitionId, List<ByteBuffer>> missingBuffers = remoteHost.getMissingBuffers(localHost.buffersByPartition);
    for (Map.Entry<PartitionId, List<ByteBuffer>> entry : missingBuffers.entrySet()) {
      assertEquals("No buffers should be missing", 0, entry.getValue().size());
    }
  }

  /**
   * Tests pausing replication for all and individual partitions. Also tests replication will pause on store that is not
   * started and resume when store restarted.
   * @throws Exception
   */
  @Test
  public void replicationPauseTest() throws Exception {
    MockClusterMap clusterMap = new MockClusterMap();
    Pair<MockHost, MockHost> localAndRemoteHosts = getLocalAndRemoteHosts(clusterMap);
    MockHost localHost = localAndRemoteHosts.getFirst();
    MockHost remoteHost = localAndRemoteHosts.getSecond();

    List<PartitionId> partitionIds = clusterMap.getAllPartitionIds(null);
    for (PartitionId partitionId : partitionIds) {
      // add  10 messages to the remote host only
      addPutMessagesToReplicasOfPartition(partitionId, Collections.singletonList(remoteHost), 10);
    }

    StoreKeyFactory storeKeyFactory = new BlobIdFactory(clusterMap);
    MockStoreKeyConverterFactory storeKeyConverterFactory = new MockStoreKeyConverterFactory(null, null);
    storeKeyConverterFactory.setConversionMap(new HashMap<>());
    storeKeyConverterFactory.setReturnInputIfAbsent(true);
    StoreKeyConverter storeKeyConverter = storeKeyConverterFactory.getStoreKeyConverter();
    Transformer transformer = new ValidatingTransformer(storeKeyFactory, storeKeyConverter);
    int batchSize = 4;
    Pair<Map<DataNodeId, List<RemoteReplicaInfo>>, ReplicaThread> replicasAndThread =
        getRemoteReplicasAndReplicaThread(batchSize, clusterMap, localHost, remoteHost, storeKeyConverter, transformer,
            null, null);
    Map<DataNodeId, List<RemoteReplicaInfo>> replicasToReplicate = replicasAndThread.getFirst();
    ReplicaThread replicaThread = replicasAndThread.getSecond();

    Map<PartitionId, Integer> progressTracker = new HashMap<>();
    PartitionId partitionToResumeFirst = clusterMap.getAllPartitionIds(null).get(0);
    PartitionId partitionToShutdownLocally = clusterMap.getAllPartitionIds(null).get(1);
    boolean allStopped = false;
    boolean onlyOneResumed = false;
    boolean allReenabled = false;
    boolean shutdownStoreRestarted = false;
    Set<PartitionId> expectedPaused = new HashSet<>();
    assertEquals("There should be no disabled partitions", expectedPaused,
        replicaThread.getReplicationDisabledPartitions());
    while (true) {
      replicaThread.replicate();
      boolean replicationDone = true;
      for (RemoteReplicaInfo replicaInfo : replicasToReplicate.get(remoteHost.dataNodeId)) {
        PartitionId id = replicaInfo.getReplicaId().getPartitionId();
        MockFindToken token = (MockFindToken) replicaInfo.getToken();
        int lastProgress = progressTracker.computeIfAbsent(id, id1 -> 0);
        int currentProgress = token.getIndex();
        boolean partDone = currentProgress + 1 == remoteHost.infosByPartition.get(id).size();
        if (allStopped || (onlyOneResumed && !id.equals(partitionToResumeFirst)) || (allReenabled
            && !shutdownStoreRestarted && id.equals(partitionToShutdownLocally))) {
          assertEquals("There should have been no progress", lastProgress, currentProgress);
        } else if (!partDone) {
          assertTrue("There has been no progress", currentProgress > lastProgress);
          progressTracker.put(id, currentProgress);
        }
        replicationDone = replicationDone && partDone;
      }
      if (!allStopped && !onlyOneResumed && !allReenabled && !shutdownStoreRestarted) {
        replicaThread.controlReplicationForPartitions(clusterMap.getAllPartitionIds(null), false);
        expectedPaused.addAll(clusterMap.getAllPartitionIds(null));
        assertEquals("Disabled partitions sets do not match", expectedPaused,
            replicaThread.getReplicationDisabledPartitions());
        allStopped = true;
      } else if (!onlyOneResumed && !allReenabled && !shutdownStoreRestarted) {
        // resume replication for first partition
        replicaThread.controlReplicationForPartitions(Collections.singletonList(partitionIds.get(0)), true);
        expectedPaused.remove(partitionIds.get(0));
        assertEquals("Disabled partitions sets do not match", expectedPaused,
            replicaThread.getReplicationDisabledPartitions());
        allStopped = false;
        onlyOneResumed = true;
      } else if (!allReenabled && !shutdownStoreRestarted) {
        // not removing the first partition
        replicaThread.controlReplicationForPartitions(clusterMap.getAllPartitionIds(null), true);
        // shutdown one local store to pause replication against that store
        localHost.storesByPartition.get(partitionToShutdownLocally).shutdown();
        onlyOneResumed = false;
        allReenabled = true;
        expectedPaused.clear();
        assertEquals("Disabled partitions sets do not match", expectedPaused,
            replicaThread.getReplicationDisabledPartitions());
      } else if (!shutdownStoreRestarted) {
        localHost.storesByPartition.get(partitionToShutdownLocally).start();
        shutdownStoreRestarted = true;
      }
      if (replicationDone) {
        break;
      }
    }

    Map<PartitionId, List<MessageInfo>> missingInfos = remoteHost.getMissingInfos(localHost.infosByPartition);
    for (Map.Entry<PartitionId, List<MessageInfo>> entry : missingInfos.entrySet()) {
      assertEquals("No infos should be missing", 0, entry.getValue().size());
    }
    Map<PartitionId, List<ByteBuffer>> missingBuffers = remoteHost.getMissingBuffers(localHost.buffersByPartition);
    for (Map.Entry<PartitionId, List<ByteBuffer>> entry : missingBuffers.entrySet()) {
      assertEquals("No buffers should be missing", 0, entry.getValue().size());
    }
  }

  /**
   * Tests that replication between a local and remote server who have different
   * blob IDs for the same blobs (via StoreKeyConverter)
   * @throws Exception
   */
  @Test
  public void replicaThreadTestConverter() throws Exception {
    MockClusterMap clusterMap = new MockClusterMap();
    Pair<MockHost, MockHost> localAndRemoteHosts = getLocalAndRemoteHosts(clusterMap);
    MockHost localHost = localAndRemoteHosts.getFirst();
    MockHost remoteHost = localAndRemoteHosts.getSecond();
    MockHost expectedLocalHost = new MockHost(localHost.dataNodeId, clusterMap);
    MockStoreKeyConverterFactory storeKeyConverterFactory = new MockStoreKeyConverterFactory(null, null);
    storeKeyConverterFactory.setConversionMap(new HashMap<>());
    storeKeyConverterFactory.setReturnInputIfAbsent(true);
    MockStoreKeyConverterFactory.MockStoreKeyConverter storeKeyConverter =
        storeKeyConverterFactory.getStoreKeyConverter();

    List<PartitionId> partitionIds = clusterMap.getWritablePartitionIds(null);
    Map<PartitionId, List<StoreKey>> idsToBeIgnoredByPartition = new HashMap<>();

    StoreKeyFactory storeKeyFactory = new BlobIdFactory(clusterMap);
    Transformer transformer = new BlobIdTransformer(storeKeyFactory, storeKeyConverter);
    int batchSize = 4;
    Pair<Map<DataNodeId, List<RemoteReplicaInfo>>, ReplicaThread> replicasAndThread =
        getRemoteReplicasAndReplicaThread(batchSize, clusterMap, localHost, remoteHost, storeKeyConverter, transformer,
            null, null);
    Map<DataNodeId, List<RemoteReplicaInfo>> replicasToReplicate = replicasAndThread.getFirst();
    ReplicaThread replicaThread = replicasAndThread.getSecond();


        /*
        STORE KEY CONVERTER MAPPING
        Key     Value
        B0      B0'
        B1      B1'
        B2      null

        BEFORE
        Local   Remote
        B0'     B0
                B1
                B2

        AFTER
        Local   Remote
        B0'     B0
        B1'     B1
                B2
        B0 is B0' for local,
        B1 is B1' for local,
        B2 is null for local,
        so it already has B0/B0'
        B1 is transferred to B1'
        and B2 is invalid for L
        so it does not count as missing
        Missing Keys: 1

     */
    Map<PartitionId, List<BlobId>> partitionIdToDeleteBlobId = new HashMap<>();
    Map<StoreKey, StoreKey> conversionMap = new HashMap<>();
    Map<PartitionId, BlobId> expectedPartitionIdToDeleteBlobId = new HashMap<>();
    for (int i = 0; i < partitionIds.size(); i++) {
      PartitionId partitionId = partitionIds.get(i);
      List<BlobId> deleteBlobIds = new ArrayList<>();
      partitionIdToDeleteBlobId.put(partitionId, deleteBlobIds);
      BlobId b0 = generateRandomBlobId(partitionId);
      deleteBlobIds.add(b0);
      BlobId b0p = generateRandomBlobId(partitionId);
      expectedPartitionIdToDeleteBlobId.put(partitionId, b0p);
      BlobId b1 = generateRandomBlobId(partitionId);
      BlobId b1p = generateRandomBlobId(partitionId);
      BlobId b2 = generateRandomBlobId(partitionId);
      deleteBlobIds.add(b2);
      conversionMap.put(b0, b0p);
      conversionMap.put(b1, b1p);
      conversionMap.put(b2, null);
      //Convert current conversion map so that BlobIdTransformer can
      //create b1p in expectedLocalHost
      storeKeyConverter.setConversionMap(conversionMap);
      storeKeyConverter.convert(conversionMap.keySet());
      addPutMessagesToReplicasOfPartition(Arrays.asList(b0p), Arrays.asList(localHost));
      addPutMessagesToReplicasOfPartition(Arrays.asList(b0, b1, b2), Arrays.asList(remoteHost));
      addPutMessagesToReplicasOfPartition(Arrays.asList(b0p, b1), Arrays.asList(null, transformer),
          Arrays.asList(expectedLocalHost));

      //Check that expected local host contains the correct blob ids
      Set<BlobId> expectedLocalHostBlobIds = new HashSet<>();
      expectedLocalHostBlobIds.add(b0p);
      expectedLocalHostBlobIds.add(b1p);
      for (MessageInfo messageInfo : expectedLocalHost.infosByPartition.get(partitionId)) {
        assertTrue("Remove should never fail", expectedLocalHostBlobIds.remove(messageInfo.getStoreKey()));
      }
      assertTrue("expectedLocalHostBlobIds should now be empty", expectedLocalHostBlobIds.isEmpty());
    }
    storeKeyConverter.setConversionMap(conversionMap);

    int expectedIndex =
        assertMissingKeysAndFixMissingStoreKeys(0, 2, batchSize, 1, replicaThread, remoteHost, replicasToReplicate);

    //Check that there are no missing buffers between expectedLocalHost and LocalHost
    Map<PartitionId, List<ByteBuffer>> missingBuffers =
        expectedLocalHost.getMissingBuffers(localHost.buffersByPartition);
    assertTrue(missingBuffers.isEmpty());
    missingBuffers = localHost.getMissingBuffers(expectedLocalHost.buffersByPartition);
    assertTrue(missingBuffers.isEmpty());

    /*
        BEFORE
        Local   Remote
        B0'     B0
        B1'     B1
                B2
                dB0 (delete B0)
                dB2

        AFTER
        Local   Remote
        B0'     B0
        B1'     B1
        dB0'    B2
                dB0
                dB2
        delete B0 gets converted
        to delete B0' in Local
        Missing Keys: 0

     */
    //delete blob
    for (int i = 0; i < partitionIds.size(); i++) {
      PartitionId partitionId = partitionIds.get(i);
      List<BlobId> deleteBlobIds = partitionIdToDeleteBlobId.get(partitionId);
      for (BlobId deleteBlobId : deleteBlobIds) {
        addDeleteMessagesToReplicasOfPartition(partitionId, deleteBlobId, Arrays.asList(remoteHost));
      }
      addDeleteMessagesToReplicasOfPartition(partitionId, expectedPartitionIdToDeleteBlobId.get(partitionId),
          Arrays.asList(expectedLocalHost));
    }

    expectedIndex = assertMissingKeysAndFixMissingStoreKeys(expectedIndex, 2, batchSize, 0, replicaThread, remoteHost,
        replicasToReplicate);

    //Check that there are no missing buffers between expectedLocalHost and LocalHost
    missingBuffers = expectedLocalHost.getMissingBuffers(localHost.buffersByPartition);
    assertTrue(missingBuffers.isEmpty());
    missingBuffers = localHost.getMissingBuffers(expectedLocalHost.buffersByPartition);
    assertTrue(missingBuffers.isEmpty());

    // 3 unconverted + 2 unconverted deleted expected missing buffers
    verifyNoMoreMissingKeysAndExpectedMissingBufferCount(remoteHost, localHost, replicaThread, replicasToReplicate,
        idsToBeIgnoredByPartition, storeKeyConverter, expectedIndex, expectedIndex, 5);
  }

  /**
   * Tests replication of TTL updates
   * @throws Exception
   */
  @Test
  public void ttlUpdateReplicationTest() throws Exception {
    MockClusterMap clusterMap = new MockClusterMap();
    Pair<MockHost, MockHost> localAndRemoteHosts = getLocalAndRemoteHosts(clusterMap);
    MockHost localHost = localAndRemoteHosts.getFirst();
    MockHost remoteHost = localAndRemoteHosts.getSecond();
    MockHost expectedLocalHost = new MockHost(localHost.dataNodeId, clusterMap);
    MockStoreKeyConverterFactory storeKeyConverterFactory = new MockStoreKeyConverterFactory(null, null);
    storeKeyConverterFactory.setConversionMap(new HashMap<>());
    storeKeyConverterFactory.setReturnInputIfAbsent(true);
    MockStoreKeyConverterFactory.MockStoreKeyConverter storeKeyConverter =
        storeKeyConverterFactory.getStoreKeyConverter();
    Map<StoreKey, StoreKey> conversionMap = new HashMap<>();
    storeKeyConverter.setConversionMap(conversionMap);
    StoreKeyFactory storeKeyFactory = new BlobIdFactory(clusterMap);
    Transformer transformer = new BlobIdTransformer(storeKeyFactory, storeKeyConverter);

    List<PartitionId> partitionIds = clusterMap.getWritablePartitionIds(null);
    int numMessagesInEachPart = 0;
    Map<PartitionId, StoreKey> idsDeletedLocallyByPartition = new HashMap<>();
    List<MockHost> remoteHostOnly = Collections.singletonList(remoteHost);
    List<MockHost> expectedLocalHostOnly = Collections.singletonList(expectedLocalHost);
    List<MockHost> localHostAndExpectedLocalHost = Arrays.asList(localHost, expectedLocalHost);
    List<MockHost> remoteHostAndExpectedLocalHost = Arrays.asList(remoteHost, expectedLocalHost);
    List<MockHost> allHosts = Arrays.asList(localHost, expectedLocalHost, remoteHost);
    for (PartitionId pid : partitionIds) {
      // add 3 put messages to both hosts (also add to expectedLocal)
      List<StoreKey> ids = addPutMessagesToReplicasOfPartition(pid, allHosts, 3);
      // delete 1 of the messages in the local host only
      addDeleteMessagesToReplicasOfPartition(pid, ids.get(0), localHostAndExpectedLocalHost);
      idsDeletedLocallyByPartition.put(pid, ids.get(0));
      // ttl update 1 of the messages in the local host only
      addTtlUpdateMessagesToReplicasOfPartition(pid, ids.get(1), localHostAndExpectedLocalHost, UPDATED_EXPIRY_TIME_MS);

      // remote host only
      // add 2 put messages
      ids.addAll(addPutMessagesToReplicasOfPartition(pid, remoteHostOnly, 1));
      ids.addAll(addPutMessagesToReplicasOfPartition(pid, remoteHostAndExpectedLocalHost, 1));
      // ttl update all 5 put messages
      for (int i = ids.size() - 1; i >= 0; i--) {
        List<MockHost> hostList = remoteHostOnly;
        if (i == 2 || i == 4) {
          hostList = remoteHostAndExpectedLocalHost;
        }
        // doing it in reverse order so that a put and ttl update arrive in the same batch
        addTtlUpdateMessagesToReplicasOfPartition(pid, ids.get(i), hostList, UPDATED_EXPIRY_TIME_MS);
      }
      // delete one of the keys that has put and ttl update on local host
      addDeleteMessagesToReplicasOfPartition(pid, ids.get(1), remoteHostAndExpectedLocalHost);
      // delete one of the keys that has put and ttl update on remote only
      addDeleteMessagesToReplicasOfPartition(pid, ids.get(3), remoteHostOnly);

      // add a TTL update and delete message without a put msg (compaction can create such a situation)
      BlobId id = generateRandomBlobId(pid);
      addTtlUpdateMessagesToReplicasOfPartition(pid, id, remoteHostOnly, UPDATED_EXPIRY_TIME_MS);
      addDeleteMessagesToReplicasOfPartition(pid, id, remoteHostOnly);

      // message transformation test cases
      // a blob ID with PUT and TTL update in both remote and local
      BlobId b0 = generateRandomBlobId(pid);
      BlobId b0p = generateRandomBlobId(pid);
      // a blob ID with a PUT in the local and PUT and TTL update in remote (with mapping)
      BlobId b1 = generateRandomBlobId(pid);
      BlobId b1p = generateRandomBlobId(pid);
      // a blob ID with PUT and TTL update in remote only (with mapping)
      BlobId b2 = generateRandomBlobId(pid);
      BlobId b2p = generateRandomBlobId(pid);
      // a blob ID with PUT and TTL update in remote (no mapping)
      BlobId b3 = generateRandomBlobId(pid);
      conversionMap.put(b0, b0p);
      conversionMap.put(b1, b1p);
      conversionMap.put(b2, b2p);
      conversionMap.put(b3, null);

      storeKeyConverter.convert(conversionMap.keySet());
      // add as required on local, remote and expected local
      // only PUT of b0p and b1p on local
      addPutMessagesToReplicasOfPartition(Arrays.asList(b0p, b1p), localHostAndExpectedLocalHost);
      // PUT of b0,b1,b2,b3 on remote
      addPutMessagesToReplicasOfPartition(Arrays.asList(b0, b1, b2, b3), remoteHostOnly);
      // PUT of b0, b1, b2 expected in local at the end
      addPutMessagesToReplicasOfPartition(Collections.singletonList(b2), Collections.singletonList(transformer),
          expectedLocalHostOnly);

      // TTL update of b0 on all hosts
      addTtlUpdateMessagesToReplicasOfPartition(pid, b0p, localHostAndExpectedLocalHost, UPDATED_EXPIRY_TIME_MS);
      addTtlUpdateMessagesToReplicasOfPartition(pid, b0, remoteHostOnly, UPDATED_EXPIRY_TIME_MS);
      // TTL update on b1, b2 and b3 on remote
      addTtlUpdateMessagesToReplicasOfPartition(pid, b1, remoteHostOnly, UPDATED_EXPIRY_TIME_MS);
      addTtlUpdateMessagesToReplicasOfPartition(pid, b1p, expectedLocalHostOnly, UPDATED_EXPIRY_TIME_MS);
      addTtlUpdateMessagesToReplicasOfPartition(pid, b2, remoteHostOnly, UPDATED_EXPIRY_TIME_MS);
      addTtlUpdateMessagesToReplicasOfPartition(pid, b2p, expectedLocalHostOnly, UPDATED_EXPIRY_TIME_MS);
      addTtlUpdateMessagesToReplicasOfPartition(pid, b3, remoteHostOnly, UPDATED_EXPIRY_TIME_MS);

      numMessagesInEachPart = remoteHost.infosByPartition.get(pid).size();
    }

    int batchSize = 4;
    Pair<Map<DataNodeId, List<RemoteReplicaInfo>>, ReplicaThread> replicasAndThread =
        getRemoteReplicasAndReplicaThread(batchSize, clusterMap, localHost, remoteHost, storeKeyConverter, transformer,
            null, null);
    List<RemoteReplicaInfo> remoteReplicaInfos = replicasAndThread.getFirst().get(remoteHost.dataNodeId);
    ReplicaThread replicaThread = replicasAndThread.getSecond();

    Map<PartitionId, List<ByteBuffer>> missingBuffers =
        expectedLocalHost.getMissingBuffers(localHost.buffersByPartition);
    for (Map.Entry<PartitionId, List<ByteBuffer>> entry : missingBuffers.entrySet()) {
      assertEquals("Missing buffers count mismatch", 7, entry.getValue().size());
    }

    // 1st iteration - 0 missing keys (3 puts already present, one put missing but del in remote, 1 ttl update will be
    // applied, 1 delete will be applied)
    // 2nd iteration - 1 missing key, 1 of which will also be ttl updated (one key with put + ttl update missing but
    // del in remote, one put and ttl update replicated)
    // 3rd iteration - 0 missing keys (1 ttl update missing but del in remote, 1 already ttl updated in iter 1, 1 key
    // already ttl updated in local, 1 key del local)
    // 4th iteration - 0 missing keys (1 key del local, 1 key already deleted, 1 key missing but del in remote, 1 key
    // with ttl update missing but del remote)
    // 5th iteration - 0 missing keys (1 key - two records - missing but del remote, 2 puts already present but TTL
    // update of one of them is applied)
    // 6th iteration - 1 missing key (put + ttl update for a key, 1 deprecated id ignored, 1 TTL update already applied)
    // 7th iteration - 0 missing keys (2 TTL updates already applied, 1 TTL update of a deprecated ID ignored)
    int[] missingKeysCounts = {0, 1, 0, 0, 0, 1, 0};
    int[] missingBuffersCount = {5, 3, 3, 3, 2, 0, 0};
    int expectedIndex = 0;
    int missingBuffersIndex = 0;

    for (int missingKeysCount : missingKeysCounts) {
      expectedIndex = Math.min(expectedIndex + batchSize, numMessagesInEachPart) - 1;
      List<ReplicaThread.ExchangeMetadataResponse> response =
          replicaThread.exchangeMetadata(new MockConnectionPool.MockConnection(remoteHost, batchSize),
              remoteReplicaInfos);
      assertEquals("Response should contain a response for each replica", remoteReplicaInfos.size(), response.size());
      for (int i = 0; i < response.size(); i++) {
        assertEquals(missingKeysCount, response.get(i).missingStoreKeys.size());
        assertEquals(expectedIndex, ((MockFindToken) response.get(i).remoteToken).getIndex());
        remoteReplicaInfos.get(i).setToken(response.get(i).remoteToken);
      }
      replicaThread.fixMissingStoreKeys(new MockConnectionPool.MockConnection(remoteHost, batchSize),
          remoteReplicaInfos, response);
      for (int i = 0; i < response.size(); i++) {
        assertEquals("Token should have been set correctly in fixMissingStoreKeys()", response.get(i).remoteToken,
            remoteReplicaInfos.get(i).getToken());
      }
      missingBuffers = expectedLocalHost.getMissingBuffers(localHost.buffersByPartition);
      for (Map.Entry<PartitionId, List<ByteBuffer>> entry : missingBuffers.entrySet()) {
        assertEquals("Missing buffers count mismatch for iteration count " + missingBuffersIndex,
            missingBuffersCount[missingBuffersIndex], entry.getValue().size());
      }
      missingBuffersIndex++;
    }

    // no more missing keys
    List<ReplicaThread.ExchangeMetadataResponse> response =
        replicaThread.exchangeMetadata(new MockConnectionPool.MockConnection(remoteHost, batchSize),
            remoteReplicaInfos);
    assertEquals("Response should contain a response for each replica", remoteReplicaInfos.size(), response.size());
    for (ReplicaThread.ExchangeMetadataResponse metadata : response) {
      assertEquals(0, metadata.missingStoreKeys.size());
      assertEquals(expectedIndex, ((MockFindToken) metadata.remoteToken).getIndex());
    }

    missingBuffers = expectedLocalHost.getMissingBuffers(localHost.buffersByPartition);
    assertEquals("There should be no missing buffers", 0, missingBuffers.size());

    // validate everything
    for (Map.Entry<PartitionId, List<MessageInfo>> remoteInfoEntry : remoteHost.infosByPartition.entrySet()) {
      List<MessageInfo> remoteInfos = remoteInfoEntry.getValue();
      List<MessageInfo> localInfos = localHost.infosByPartition.get(remoteInfoEntry.getKey());
      Set<StoreKey> seen = new HashSet<>();
      for (MessageInfo remoteInfo : remoteInfos) {
        StoreKey remoteId = remoteInfo.getStoreKey();
        if (seen.add(remoteId)) {
          StoreKey localId = storeKeyConverter.convert(Collections.singleton(remoteId)).get(remoteId);
          MessageInfo localInfo = getMessageInfo(localId, localInfos, false, false);
          if (localId == null) {
            // this is a deprecated ID. There should be no messages locally
            assertNull(remoteId + " is deprecated and should have no entries", localInfo);
          } else {
            MessageInfo mergedRemoteInfo = getMergedMessageInfo(remoteId, remoteInfos);
            if (localInfo == null) {
              // local has no put, must be deleted on remote
              assertTrue(localId + ":" + remoteId + " not replicated", mergedRemoteInfo.isDeleted());
            } else {
              // local has a put and must be either at or beyond the state of the remote (based on ops above)
              MessageInfo mergedLocalInfo = getMergedMessageInfo(localId, localInfos);
              if (mergedRemoteInfo.isDeleted()) {
                // delete on remote, should be deleted locally too
                assertTrue(localId + ":" + remoteId + " is deleted on remote but not locally",
                    mergedLocalInfo.isDeleted());
              } else if (mergedRemoteInfo.isTtlUpdated() && !idsDeletedLocallyByPartition.get(remoteInfoEntry.getKey())
                  .equals(localId)) {
                // ttl updated on remote, should be ttl updated locally too
                assertTrue(localId + ":" + remoteId + " is updated on remote but not locally",
                    mergedLocalInfo.isTtlUpdated());
              } else if (!idsDeletedLocallyByPartition.get(remoteInfoEntry.getKey()).equals(localId)) {
                // should not be updated or deleted locally
                assertFalse(localId + ":" + remoteId + " has been updated", mergedLocalInfo.isTtlUpdated());
                assertFalse(localId + ":" + remoteId + " has been deleted", mergedLocalInfo.isDeleted());
              }
            }
          }
        }
      }
    }
  }

  /**
   * Test the case where a blob gets deleted after a replication metadata exchange completes and identifies the blob as
   * a candidate. The subsequent GetRequest should succeed as Replication makes a Include_All call, and
   * fixMissingStoreKeys() should succeed without exceptions. The blob should not be put locally.
   */
  @Test
  public void deletionAfterMetadataExchangeTest() throws Exception {
    int batchSize = 400;
    ReplicationTestSetup testSetup = new ReplicationTestSetup(batchSize);
    short blobIdVersion = CommonTestUtils.getCurrentBlobIdVersion();
    List<PartitionId> partitionIds = testSetup.partitionIds;
    MockHost remoteHost = testSetup.remoteHost;
    MockHost localHost = testSetup.localHost;
    Map<PartitionId, Set<StoreKey>> idsToExpectByPartition = new HashMap<>();
    for (int i = 0; i < partitionIds.size(); i++) {
      PartitionId partitionId = partitionIds.get(i);

      // add 5 messages to remote host only.
      Set<StoreKey> expectedIds =
          new HashSet<>(addPutMessagesToReplicasOfPartition(partitionId, Collections.singletonList(remoteHost), 5));

      short accountId = Utils.getRandomShort(TestUtils.RANDOM);
      short containerId = Utils.getRandomShort(TestUtils.RANDOM);
      boolean toEncrypt = TestUtils.RANDOM.nextBoolean();

      // add an expired message to the remote host only
      StoreKey id =
          new BlobId(blobIdVersion, BlobId.BlobIdType.NATIVE, ClusterMapUtils.UNKNOWN_DATACENTER_ID, accountId,
              containerId, partitionId, toEncrypt, BlobId.BlobDataType.DATACHUNK);
      PutMsgInfoAndBuffer msgInfoAndBuffer = createPutMessage(id, accountId, containerId, toEncrypt);
      remoteHost.addMessage(partitionId,
          new MessageInfo(id, msgInfoAndBuffer.byteBuffer.remaining(), 1, accountId, containerId,
              msgInfoAndBuffer.messageInfo.getOperationTimeMs()), msgInfoAndBuffer.byteBuffer);

      // add 3 messages to the remote host only
      expectedIds.addAll(addPutMessagesToReplicasOfPartition(partitionId, Collections.singletonList(remoteHost), 3));

      // delete the very first blob in the remote host only (and delete it from expected list)
      Iterator<StoreKey> iter = expectedIds.iterator();
      addDeleteMessagesToReplicasOfPartition(partitionId, iter.next(), Collections.singletonList(remoteHost));
      iter.remove();

      // PUT and DELETE a blob in the remote host only
      id = addPutMessagesToReplicasOfPartition(partitionId, Collections.singletonList(remoteHost), 1).get(0);
      addDeleteMessagesToReplicasOfPartition(partitionId, id, Collections.singletonList(remoteHost));

      idsToExpectByPartition.put(partitionId, expectedIds);
    }

    // Do the replica metadata exchange.
    List<ReplicaThread.ExchangeMetadataResponse> responses =
        testSetup.replicaThread.exchangeMetadata(new MockConnectionPool.MockConnection(remoteHost, batchSize),
            testSetup.replicasToReplicate.get(remoteHost.dataNodeId));

    Assert.assertEquals("Actual keys in Exchange Metadata Response different from expected",
        idsToExpectByPartition.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()),
        responses.stream().map(k -> k.missingStoreKeys).flatMap(Collection::stream).collect(Collectors.toSet()));

    // Now delete a message in the remote before doing the Get requests (for every partition). Remove these keys from
    // expected key set. Even though they are requested, they should not go into the local store. However, this cycle
    // of replication must be successful.
    for (PartitionId partitionId : partitionIds) {
      Iterator<StoreKey> iter = idsToExpectByPartition.get(partitionId).iterator();
      iter.next();
      StoreKey keyToDelete = iter.next();
      addDeleteMessagesToReplicasOfPartition(partitionId, keyToDelete, Collections.singletonList(remoteHost));
      iter.remove();
    }

    testSetup.replicaThread.fixMissingStoreKeys(new MockConnectionPool.MockConnection(remoteHost, batchSize),
        testSetup.replicasToReplicate.get(remoteHost.dataNodeId), responses);

    Assert.assertEquals(idsToExpectByPartition.keySet(), localHost.infosByPartition.keySet());
    Assert.assertEquals("Actual keys in Exchange Metadata Response different from expected",
        idsToExpectByPartition.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()),
        localHost.infosByPartition.values()
            .stream()
            .flatMap(Collection::stream)
            .map(MessageInfo::getStoreKey)
            .collect(Collectors.toSet()));
  }

  /**
   * Test the case where a blob expires after a replication metadata exchange completes and identifies the blob as
   * a candidate. The subsequent GetRequest should succeed as Replication makes a Include_All call, and
   * fixMissingStoreKeys() should succeed without exceptions. The blob should not be put locally.
   */
  @Test
  public void expiryAfterMetadataExchangeTest() throws Exception {
    int batchSize = 400;
    ReplicationTestSetup testSetup = new ReplicationTestSetup(batchSize);
    List<PartitionId> partitionIds = testSetup.partitionIds;
    MockHost remoteHost = testSetup.remoteHost;
    MockHost localHost = testSetup.localHost;
    short blobIdVersion = CommonTestUtils.getCurrentBlobIdVersion();
    Map<PartitionId, Set<StoreKey>> idsToExpectByPartition = new HashMap<>();
    for (int i = 0; i < partitionIds.size(); i++) {
      PartitionId partitionId = partitionIds.get(i);

      // add 5 messages to remote host only.
      Set<StoreKey> expectedIds =
          new HashSet<>(addPutMessagesToReplicasOfPartition(partitionId, Collections.singletonList(remoteHost), 5));

      short accountId = Utils.getRandomShort(TestUtils.RANDOM);
      short containerId = Utils.getRandomShort(TestUtils.RANDOM);
      boolean toEncrypt = TestUtils.RANDOM.nextBoolean();

      // add an expired message to the remote host only
      StoreKey id =
          new BlobId(blobIdVersion, BlobId.BlobIdType.NATIVE, ClusterMapUtils.UNKNOWN_DATACENTER_ID, accountId,
              containerId, partitionId, toEncrypt, BlobId.BlobDataType.DATACHUNK);
      PutMsgInfoAndBuffer msgInfoAndBuffer = createPutMessage(id, accountId, containerId, toEncrypt);
      remoteHost.addMessage(partitionId,
          new MessageInfo(id, msgInfoAndBuffer.byteBuffer.remaining(), 1, accountId, containerId,
              msgInfoAndBuffer.messageInfo.getOperationTimeMs()), msgInfoAndBuffer.byteBuffer);

      // add 3 messages to the remote host only
      expectedIds.addAll(addPutMessagesToReplicasOfPartition(partitionId, Collections.singletonList(remoteHost), 3));

      // delete the very first blob in the remote host only (and delete it from expected list)
      Iterator<StoreKey> iter = expectedIds.iterator();
      addDeleteMessagesToReplicasOfPartition(partitionId, iter.next(), Collections.singletonList(remoteHost));
      iter.remove();

      // PUT and DELETE a blob in the remote host only
      id = addPutMessagesToReplicasOfPartition(partitionId, Collections.singletonList(remoteHost), 1).get(0);
      addDeleteMessagesToReplicasOfPartition(partitionId, id, Collections.singletonList(remoteHost));

      idsToExpectByPartition.put(partitionId, expectedIds);
    }

    // Do the replica metadata exchange.
    List<ReplicaThread.ExchangeMetadataResponse> responses =
        testSetup.replicaThread.exchangeMetadata(new MockConnectionPool.MockConnection(remoteHost, batchSize),
            testSetup.replicasToReplicate.get(remoteHost.dataNodeId));

    Assert.assertEquals("Actual keys in Exchange Metadata Response different from expected",
        idsToExpectByPartition.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()),
        responses.stream().map(k -> k.missingStoreKeys).flatMap(Collection::stream).collect(Collectors.toSet()));

    // Now expire a message in the remote before doing the Get requests (for every partition). Remove these keys from
    // expected key set. Even though they are requested, they should not go into the local store. However, this cycle
    // of replication must be successful.
    PartitionId partitionId = idsToExpectByPartition.keySet().iterator().next();
    Iterator<StoreKey> keySet = idsToExpectByPartition.get(partitionId).iterator();
    StoreKey keyToExpire = keySet.next();
    keySet.remove();
    MessageInfo msgInfoToExpire = null;

    for (MessageInfo info : remoteHost.infosByPartition.get(partitionId)) {
      if (info.getStoreKey().equals(keyToExpire)) {
        msgInfoToExpire = info;
        break;
      }
    }

    int i = remoteHost.infosByPartition.get(partitionId).indexOf(msgInfoToExpire);
    remoteHost.infosByPartition.get(partitionId)
        .set(i, new MessageInfo(msgInfoToExpire.getStoreKey(), msgInfoToExpire.getSize(), msgInfoToExpire.isDeleted(),
            msgInfoToExpire.isTtlUpdated(), 1, msgInfoToExpire.getAccountId(), msgInfoToExpire.getContainerId(),
            msgInfoToExpire.getOperationTimeMs()));

    testSetup.replicaThread.fixMissingStoreKeys(new MockConnectionPool.MockConnection(remoteHost, batchSize),
        testSetup.replicasToReplicate.get(remoteHost.dataNodeId), responses);

    Assert.assertEquals(idsToExpectByPartition.keySet(), localHost.infosByPartition.keySet());
    Assert.assertEquals("Actual keys in Exchange Metadata Response different from expected",
        idsToExpectByPartition.values().stream().flatMap(Collection::stream).collect(Collectors.toSet()),
        localHost.infosByPartition.values()
            .stream()
            .flatMap(Collection::stream)
            .map(MessageInfo::getStoreKey)
            .collect(Collectors.toSet()));
  }

  /**
   * Test the case where remote host has a sequence of Old_Put, Old_Delete, New_Put messages and local host is initially
   * empty. Verify that local host is empty after replication.
   */
  @Test
  public void replicateWithOldPutDeleteAndNewPutTest() throws Exception {
    ReplicationTestSetup testSetup = new ReplicationTestSetup(10);
    Pair<String, String> testCaseAndExpectResult = new Pair<>("OP OD NP", "");
    createMixedMessagesOnRemoteHost(testSetup, testCaseAndExpectResult.getFirst());
    replicateAndVerify(testSetup, testCaseAndExpectResult.getSecond());
  }

  /**
   * Test the case where remote host has a sequence of Old_Put, New_Delete messages and local host is initially empty.
   * Verify that local host only has New_Put after replication.
   */
  @Test
  public void replicateWithOldPutAndNewDeleteTest() throws Exception {
    ReplicationTestSetup testSetup = new ReplicationTestSetup(10);
    Pair<String, String> testCaseAndExpectResult = new Pair<>("OP ND", "NP");
    createMixedMessagesOnRemoteHost(testSetup, testCaseAndExpectResult.getFirst());
    replicateAndVerify(testSetup, testCaseAndExpectResult.getSecond());
  }

  /**
   * Test the case where remote host has a sequence of Old_Put, New_Put, Old_Delete messages and local host is initially empty.
   * Verify that local host is empty after replication.
   */
  @Test
  public void replicateWithOldPutNewPutAndOldDeleteTest() throws Exception {
    ReplicationTestSetup testSetup = new ReplicationTestSetup(10);
    Pair<String, String> testCaseAndExpectResult = new Pair<>("OP NP OD", "");
    createMixedMessagesOnRemoteHost(testSetup, testCaseAndExpectResult.getFirst());
    replicateAndVerify(testSetup, testCaseAndExpectResult.getSecond());
  }

  /**
   * Test the case where remote host has a sequence of Old_Put, New_Put messages and local host is initially empty.
   * Verify that local host only has New_Put after replication.
   */
  @Test
  public void replicateWithOldPutAndNewPutTest() throws Exception {
    ReplicationTestSetup testSetup = new ReplicationTestSetup(10);
    Pair<String, String> testCaseAndExpectResult = new Pair<>("OP NP", "NP");
    createMixedMessagesOnRemoteHost(testSetup, testCaseAndExpectResult.getFirst());
    replicateAndVerify(testSetup, testCaseAndExpectResult.getSecond());
  }

  /**
   * Test the case where remote host has a sequence of Old_Put, New_Put, Old_Delete, New_Delete messages and local host
   * is initially empty. Verify that local host is empty after replication.
   */
  @Test
  public void replicateWithOldPutNewPutOldDeleteAndNewDeleteTest() throws Exception {
    ReplicationTestSetup testSetup = new ReplicationTestSetup(10);
    Pair<String, String> testCaseAndExpectResult = new Pair<>("OP NP OD ND", "");
    createMixedMessagesOnRemoteHost(testSetup, testCaseAndExpectResult.getFirst());
    replicateAndVerify(testSetup, testCaseAndExpectResult.getSecond());
  }

  /**
   * Test the case where remote host has a sequence of New_Put, New_Delete, Old_Put messages and local host is initially empty.
   * Verify that local host only has New_Put after replication.
   */
  @Test
  public void replicateWithNewPutDeleteAndOldPutTest() throws Exception {
    ReplicationTestSetup testSetup = new ReplicationTestSetup(10);
    Pair<String, String> testCaseAndExpectResult = new Pair<>("NP ND OP", "NP");
    createMixedMessagesOnRemoteHost(testSetup, testCaseAndExpectResult.getFirst());
    replicateAndVerify(testSetup, testCaseAndExpectResult.getSecond());
  }

  /**
   * Tests {@link ReplicaThread#exchangeMetadata(ConnectedChannel, List)} and
   * {@link ReplicaThread#fixMissingStoreKeys(ConnectedChannel, List, List)} for valid puts, deletes, expired keys and
   * corrupt blobs.
   * @throws Exception
   */
  @Test
  public void replicaThreadTest() throws Exception {
    MockClusterMap clusterMap = new MockClusterMap();
    Pair<MockHost, MockHost> localAndRemoteHosts = getLocalAndRemoteHosts(clusterMap);
    MockHost localHost = localAndRemoteHosts.getFirst();
    MockHost remoteHost = localAndRemoteHosts.getSecond();
    MockStoreKeyConverterFactory storeKeyConverterFactory = new MockStoreKeyConverterFactory(null, null);
    storeKeyConverterFactory.setConversionMap(new HashMap<>());
    storeKeyConverterFactory.setReturnInputIfAbsent(true);
    MockStoreKeyConverterFactory.MockStoreKeyConverter storeKeyConverter =
        storeKeyConverterFactory.getStoreKeyConverter();

    short blobIdVersion = CommonTestUtils.getCurrentBlobIdVersion();
    List<PartitionId> partitionIds = clusterMap.getWritablePartitionIds(null);
    Map<PartitionId, List<StoreKey>> idsToBeIgnoredByPartition = new HashMap<>();
    for (int i = 0; i < partitionIds.size(); i++) {
      List<StoreKey> idsToBeIgnored = new ArrayList<>();
      PartitionId partitionId = partitionIds.get(i);
      // add 6 messages to both hosts.
      StoreKey toDeleteId =
          addPutMessagesToReplicasOfPartition(partitionId, Arrays.asList(localHost, remoteHost), 6).get(0);

      short accountId = Utils.getRandomShort(TestUtils.RANDOM);
      short containerId = Utils.getRandomShort(TestUtils.RANDOM);
      boolean toEncrypt = TestUtils.RANDOM.nextBoolean();
      // add an expired message to the remote host only
      StoreKey id =
          new BlobId(blobIdVersion, BlobId.BlobIdType.NATIVE, ClusterMapUtils.UNKNOWN_DATACENTER_ID, accountId,
              containerId, partitionId, toEncrypt, BlobId.BlobDataType.DATACHUNK);
      PutMsgInfoAndBuffer msgInfoAndBuffer = createPutMessage(id, accountId, containerId, toEncrypt);
      remoteHost.addMessage(partitionId,
          new MessageInfo(id, msgInfoAndBuffer.byteBuffer.remaining(), 1, accountId, containerId,
              msgInfoAndBuffer.messageInfo.getOperationTimeMs()), msgInfoAndBuffer.byteBuffer);
      idsToBeIgnored.add(id);

      // add 3 messages to the remote host only
      addPutMessagesToReplicasOfPartition(partitionId, Collections.singletonList(remoteHost), 3);

      accountId = Utils.getRandomShort(TestUtils.RANDOM);
      containerId = Utils.getRandomShort(TestUtils.RANDOM);
      toEncrypt = TestUtils.RANDOM.nextBoolean();
      // add a corrupt message to the remote host only
      id = new BlobId(blobIdVersion, BlobId.BlobIdType.NATIVE, ClusterMapUtils.UNKNOWN_DATACENTER_ID, accountId,
          containerId, partitionId, toEncrypt, BlobId.BlobDataType.DATACHUNK);
      msgInfoAndBuffer = createPutMessage(id, accountId, containerId, toEncrypt);
      byte[] data = msgInfoAndBuffer.byteBuffer.array();
      // flip every bit in the array
      for (int j = 0; j < data.length; j++) {
        data[j] ^= 0xFF;
      }
      remoteHost.addMessage(partitionId, msgInfoAndBuffer.messageInfo, msgInfoAndBuffer.byteBuffer);
      idsToBeIgnored.add(id);

      // add 3 messages to the remote host only
      addPutMessagesToReplicasOfPartition(partitionId, Collections.singletonList(remoteHost), 3);

      // add delete record for the very first blob in the remote host only
      addDeleteMessagesToReplicasOfPartition(partitionId, toDeleteId, Collections.singletonList(remoteHost));
      // PUT and DELETE a blob in the remote host only
      id = addPutMessagesToReplicasOfPartition(partitionId, Collections.singletonList(remoteHost), 1).get(0);
      addDeleteMessagesToReplicasOfPartition(partitionId, id, Collections.singletonList(remoteHost));
      idsToBeIgnored.add(id);

      // add 2 or 3 messages (depending on whether partition is even-numbered or odd-numbered) to the remote host only
      addPutMessagesToReplicasOfPartition(partitionId, Collections.singletonList(remoteHost), i % 2 == 0 ? 2 : 3);

      idsToBeIgnoredByPartition.put(partitionId, idsToBeIgnored);

      // ensure that the first key is not deleted in the local host
      assertNull(toDeleteId + " should not be deleted in the local host",
          getMessageInfo(toDeleteId, localHost.infosByPartition.get(partitionId), true, false));
    }

    StoreKeyFactory storeKeyFactory = new BlobIdFactory(clusterMap);
    Transformer transformer = new BlobIdTransformer(storeKeyFactory, storeKeyConverter);
    int batchSize = 4;
    Pair<Map<DataNodeId, List<RemoteReplicaInfo>>, ReplicaThread> replicasAndThread =
        getRemoteReplicasAndReplicaThread(batchSize, clusterMap, localHost, remoteHost, storeKeyConverter, transformer,
            null, null);
    Map<DataNodeId, List<RemoteReplicaInfo>> replicasToReplicate = replicasAndThread.getFirst();
    ReplicaThread replicaThread = replicasAndThread.getSecond();

    Map<PartitionId, List<ByteBuffer>> missingBuffers = remoteHost.getMissingBuffers(localHost.buffersByPartition);
    for (Map.Entry<PartitionId, List<ByteBuffer>> entry : missingBuffers.entrySet()) {
      if (partitionIds.indexOf(entry.getKey()) % 2 == 0) {
        assertEquals("Missing buffers count mismatch", 13, entry.getValue().size());
      } else {
        assertEquals("Missing buffers count mismatch", 14, entry.getValue().size());
      }
    }

    // 1st and 2nd iterations - no keys missing because all data is in both hosts
    // 3rd iteration - 3 missing keys (one expired)
    // 4th iteration - 3 missing keys (one expired) - the corrupt key also shows up as missing but is ignored later
    // 5th iteration - 1 missing key (1 key from prev cycle, 1 deleted key, 1 never present key but deleted in remote)
    // 6th iteration - 2 missing keys (2 entries i.e put,delete of never present key)
    int[] missingKeysCounts = {0, 0, 3, 3, 1, 2};
    int[] missingBuffersCount = {12, 12, 9, 7, 6, 4};
    int expectedIndex = 0;
    int missingBuffersIndex = 0;

    for (int missingKeysCount : missingKeysCounts) {
      expectedIndex = assertMissingKeysAndFixMissingStoreKeys(expectedIndex, batchSize - 1, batchSize, missingKeysCount,
          replicaThread, remoteHost, replicasToReplicate);

      missingBuffers = remoteHost.getMissingBuffers(localHost.buffersByPartition);
      for (Map.Entry<PartitionId, List<ByteBuffer>> entry : missingBuffers.entrySet()) {
        if (partitionIds.indexOf(entry.getKey()) % 2 == 0) {
          assertEquals("Missing buffers count mismatch for iteration count " + missingBuffersIndex,
              missingBuffersCount[missingBuffersIndex], entry.getValue().size());
        } else {
          assertEquals("Missing buffers count mismatch for iteration count " + missingBuffersIndex,
              missingBuffersCount[missingBuffersIndex] + 1, entry.getValue().size());
        }
      }
      missingBuffersIndex++;
    }

    // Test the case where some partitions have missing keys, but not all.
    List<ReplicaThread.ExchangeMetadataResponse> response =
        replicaThread.exchangeMetadata(new MockConnectionPool.MockConnection(remoteHost, batchSize),
            replicasToReplicate.get(remoteHost.dataNodeId));
    assertEquals("Response should contain a response for each replica",
        replicasToReplicate.get(remoteHost.dataNodeId).size(), response.size());
    for (int i = 0; i < response.size(); i++) {
      if (i % 2 == 0) {
        assertEquals(0, response.get(i).missingStoreKeys.size());
        assertEquals(expectedIndex, ((MockFindToken) response.get(i).remoteToken).getIndex());
      } else {
        assertEquals(1, response.get(i).missingStoreKeys.size());
        assertEquals(expectedIndex + 1, ((MockFindToken) response.get(i).remoteToken).getIndex());
      }
    }
    replicaThread.fixMissingStoreKeys(new MockConnectionPool.MockConnection(remoteHost, batchSize),
        replicasToReplicate.get(remoteHost.dataNodeId), response);
    for (int i = 0; i < response.size(); i++) {
      assertEquals("Token should have been set correctly in fixMissingStoreKeys()", response.get(i).remoteToken,
          replicasToReplicate.get(remoteHost.dataNodeId).get(i).getToken());
    }

    // 1 expired + 1 corrupt + 1 put (never present) + 1 deleted (never present) expected missing buffers
    verifyNoMoreMissingKeysAndExpectedMissingBufferCount(remoteHost, localHost, replicaThread, replicasToReplicate,
        idsToBeIgnoredByPartition, storeKeyConverter, expectedIndex, expectedIndex + 1, 4);
  }

  @Test
  public void replicaThreadSleepTest() throws Exception {
    MockClusterMap clusterMap = new MockClusterMap();
    Pair<MockHost, MockHost> localAndRemoteHosts = getLocalAndRemoteHosts(clusterMap);
    MockHost localHost = localAndRemoteHosts.getFirst();
    MockHost remoteHost = localAndRemoteHosts.getSecond();
    long expectedThrottleDurationMs =
        localHost.dataNodeId.getDatacenterName().equals(remoteHost.dataNodeId.getDatacenterName())
            ? replicationConfig.replicationIntraReplicaThreadThrottleSleepDurationMs
            : replicationConfig.replicationInterReplicaThreadThrottleSleepDurationMs;
    MockStoreKeyConverterFactory storeKeyConverterFactory = new MockStoreKeyConverterFactory(null, null);
    storeKeyConverterFactory.setConversionMap(new HashMap<>());
    storeKeyConverterFactory.setReturnInputIfAbsent(true);
    MockStoreKeyConverterFactory.MockStoreKeyConverter storeKeyConverter =
        storeKeyConverterFactory.getStoreKeyConverter();

    StoreKeyFactory storeKeyFactory = new BlobIdFactory(clusterMap);
    Transformer transformer = new BlobIdTransformer(storeKeyFactory, storeKeyConverter);
    int batchSize = 4;
    Pair<Map<DataNodeId, List<RemoteReplicaInfo>>, ReplicaThread> replicasAndThread =
        getRemoteReplicasAndReplicaThread(batchSize, clusterMap, localHost, remoteHost, storeKeyConverter, transformer,
            null, null);
    Map<DataNodeId, List<RemoteReplicaInfo>> replicasToReplicate = replicasAndThread.getFirst();
    ReplicaThread replicaThread = replicasAndThread.getSecond();

    // populate data, add 1 messages to both hosts.
    for (PartitionId partitionId : clusterMap.getAllPartitionIds(null)) {
      addPutMessagesToReplicasOfPartition(partitionId, Arrays.asList(localHost, remoteHost), 1);
    }
    // tests to verify replica thread throttling and idling functions in the following steps:
    // 1. all replicas are in sync, thread level sleep and replica quarantine are both enabled.
    // 2. add put messages to some replica and verify that replication for replicas remain disabled.
    // 3. forward the time so replication for replicas are re-enabled and check replication resumes.
    // 4. add more put messages to ensure replication happens continuously when needed and is throttled appropriately.

    // 1. verify that the replica thread sleeps and replicas are temporarily disable when all replicas are synced.
    List<List<RemoteReplicaInfo>> replicasToReplicateList = new ArrayList<>(replicasToReplicate.values());
    // replicate is called and time is moved forward to prepare the replicas for testing.
    replicaThread.replicate();
    time.sleep(replicationConfig.replicationSyncedReplicaBackoffDurationMs + 1);
    long currentTimeMs = time.milliseconds();
    replicaThread.replicate();
    for (List<RemoteReplicaInfo> replicaInfos : replicasToReplicateList) {
      for (RemoteReplicaInfo replicaInfo : replicaInfos) {
        assertEquals("Unexpected re-enable replication time",
            currentTimeMs + replicationConfig.replicationSyncedReplicaBackoffDurationMs,
            replicaInfo.getReEnableReplicationTime());
      }
    }
    currentTimeMs = time.milliseconds();
    replicaThread.replicate();
    assertEquals("Replicas are in sync, replica thread should sleep by replication.thread.idle.sleep.duration.ms",
        currentTimeMs + replicationConfig.replicationReplicaThreadIdleSleepDurationMs, time.milliseconds());

    // 2. add 3 messages to a partition in the remote host only and verify replication for all replicas should be disabled.
    PartitionId partitionId = clusterMap.getWritablePartitionIds(null).get(0);
    addPutMessagesToReplicasOfPartition(partitionId, Collections.singletonList(remoteHost), 3);
    int[] missingKeys = new int[replicasToReplicate.get(remoteHost.dataNodeId).size()];
    for (int i = 0; i < missingKeys.length; i++) {
      missingKeys[i] = replicasToReplicate.get(remoteHost.dataNodeId)
          .get(i)
          .getReplicaId()
          .getPartitionId()
          .isEqual(partitionId.toString()) ? 3 : 0;
    }
    currentTimeMs = time.milliseconds();
    replicaThread.replicate();
    assertEquals("Replication for all replicas should be disabled and the thread should sleep",
        currentTimeMs + replicationConfig.replicationReplicaThreadIdleSleepDurationMs, time.milliseconds());
    assertMissingKeys(missingKeys, batchSize, replicaThread, remoteHost, replicasToReplicate);

    // 3. forward the time and run replicate and verify the replication.
    time.sleep(replicationConfig.replicationSyncedReplicaBackoffDurationMs);
    replicaThread.replicate();
    missingKeys = new int[replicasToReplicate.get(remoteHost.dataNodeId).size()];
    assertMissingKeys(missingKeys, batchSize, replicaThread, remoteHost, replicasToReplicate);

    // 4. add more put messages and verify that replication continues and is throttled appropriately.
    addPutMessagesToReplicasOfPartition(partitionId, Arrays.asList(localHost, remoteHost), 3);
    currentTimeMs = time.milliseconds();
    replicaThread.replicate();
    assertMissingKeys(missingKeys, batchSize, replicaThread, remoteHost, replicasToReplicate);
    assertEquals("Replica thread should sleep exactly " + expectedThrottleDurationMs + " since remote has new token",
        currentTimeMs + expectedThrottleDurationMs, time.milliseconds());

    // verify that throttling on the replica thread is disabled when relevant configs are 0.
    Properties properties = new Properties();
    properties.setProperty("replication.intra.replica.thread.throttle.sleep.duration.ms", "0");
    properties.setProperty("replication.inter.replica.thread.throttle.sleep.duration.ms", "0");
    replicationConfig = new ReplicationConfig(new VerifiableProperties(properties));
    replicasAndThread =
        getRemoteReplicasAndReplicaThread(batchSize, clusterMap, localHost, remoteHost, storeKeyConverter, transformer,
            null, null);
    replicaThread = replicasAndThread.getSecond();
    currentTimeMs = time.milliseconds();
    replicaThread.replicate();
    assertEquals("Replica thread should not sleep when throttling is disabled and replicas are out of sync",
        currentTimeMs, time.milliseconds());
  }

  /**
   * Tests {@link ReplicationMetrics#getMaxLagForPartition(PartitionId)}
   * @throws Exception
   */
  @Test
  public void replicationLagMetricAndSyncUpTest() throws Exception {
    MockClusterMap clusterMap = new MockClusterMap();
    ClusterMapConfig clusterMapConfig = new ClusterMapConfig(verifiableProperties);
    AmbryReplicaSyncUpManager replicaSyncUpService = new AmbryReplicaSyncUpManager(clusterMapConfig);
    Pair<MockHost, MockHost> localAndRemoteHosts = getLocalAndRemoteHosts(clusterMap);
    MockHost localHost = localAndRemoteHosts.getFirst();
    MockHost remoteHost1 = localAndRemoteHosts.getSecond();
    // create another remoteHost2 that shares spacial partition with localHost and remoteHost1
    PartitionId specialPartitionId = clusterMap.getWritablePartitionIds(MockClusterMap.SPECIAL_PARTITION_CLASS).get(0);
    MockHost remoteHost2 = new MockHost(specialPartitionId.getReplicaIds().get(2).getDataNodeId(), clusterMap);
    MockStoreKeyConverterFactory storeKeyConverterFactory = new MockStoreKeyConverterFactory(null, null);
    storeKeyConverterFactory.setConversionMap(new HashMap<>());
    storeKeyConverterFactory.setReturnInputIfAbsent(true);
    MockStoreKeyConverterFactory.MockStoreKeyConverter storeKeyConverter =
        storeKeyConverterFactory.getStoreKeyConverter();
    int batchSize = 4;

    List<PartitionId> partitionIds = clusterMap.getWritablePartitionIds(null);
    for (int i = 0; i < partitionIds.size(); i++) {
      PartitionId partitionId = partitionIds.get(i);
      // add batchSize + 1 messages to the remoteHost1 so that two round of replication is needed.
      addPutMessagesToReplicasOfPartition(partitionId, Collections.singletonList(remoteHost1), batchSize + 1);
    }
    // add batchSize - 1 messages to the remoteHost2 so that localHost can catch up during one cycle of replication
    for (ReplicaId replicaId : clusterMap.getReplicaIds(remoteHost2.dataNodeId)) {
      addPutMessagesToReplicasOfPartition(replicaId.getPartitionId(), Collections.singletonList(remoteHost2),
          batchSize - 1);
    }

    StoreKeyFactory storeKeyFactory = new BlobIdFactory(clusterMap);
    Transformer transformer = new BlobIdTransformer(storeKeyFactory, storeKeyConverter);
    Pair<Map<DataNodeId, List<RemoteReplicaInfo>>, ReplicaThread> replicasAndThread1 =
        getRemoteReplicasAndReplicaThread(batchSize, clusterMap, localHost, remoteHost1, storeKeyConverter, transformer,
            null, replicaSyncUpService);
    Map<DataNodeId, List<RemoteReplicaInfo>> replicasToReplicate1 = replicasAndThread1.getFirst();
    ReplicaThread replicaThread1 = replicasAndThread1.getSecond();
    clusterMap.getReplicaIds(localHost.dataNodeId).forEach(replicaSyncUpService::initiateBootstrap);

    List<ReplicaThread.ExchangeMetadataResponse> response =
        replicaThread1.exchangeMetadata(new MockConnectionPool.MockConnection(remoteHost1, batchSize),
            replicasToReplicate1.get(remoteHost1.dataNodeId));
    replicaThread1.fixMissingStoreKeys(new MockConnectionPool.MockConnection(remoteHost1, batchSize),
        replicasToReplicate1.get(remoteHost1.dataNodeId), response);
    for (PartitionId partitionId : partitionIds) {
      List<MessageInfo> allMessageInfos = localAndRemoteHosts.getSecond().infosByPartition.get(partitionId);
      long expectedLag =
          allMessageInfos.subList(batchSize, allMessageInfos.size()).stream().mapToLong(i -> i.getSize()).sum();
      assertEquals("Replication lag doesn't match expected value", expectedLag,
          replicaThread1.getReplicationMetrics().getMaxLagForPartition(partitionId));
    }

    response = replicaThread1.exchangeMetadata(new MockConnectionPool.MockConnection(remoteHost1, batchSize),
        replicasToReplicate1.get(remoteHost1.dataNodeId));
    replicaThread1.fixMissingStoreKeys(new MockConnectionPool.MockConnection(remoteHost1, batchSize),
        replicasToReplicate1.get(remoteHost1.dataNodeId), response);
    for (PartitionId partitionId : partitionIds) {
      assertEquals("Replication lag should equal to 0", 0,
          replicaThread1.getReplicationMetrics().getMaxLagForPartition(partitionId));
    }

    // replicate with remoteHost2 to ensure special replica has caught up with enough peers
    Pair<Map<DataNodeId, List<RemoteReplicaInfo>>, ReplicaThread> replicasAndThread2 =
        getRemoteReplicasAndReplicaThread(batchSize, clusterMap, localHost, remoteHost2, storeKeyConverter, transformer,
            null, replicaSyncUpService);
    Map<DataNodeId, List<RemoteReplicaInfo>> replicasToReplicate2 = replicasAndThread2.getFirst();
    ReplicaThread replicaThread2 = replicasAndThread2.getSecond();
    response = replicaThread2.exchangeMetadata(new MockConnectionPool.MockConnection(remoteHost2, batchSize),
        replicasToReplicate2.get(remoteHost2.dataNodeId));
    replicaThread2.fixMissingStoreKeys(new MockConnectionPool.MockConnection(remoteHost2, batchSize),
        replicasToReplicate2.get(remoteHost2.dataNodeId), response);
    // verify replica of special partition has completed bootstrap and becomes standby
    RemoteReplicaInfo specialReplicaInfo = replicasToReplicate2.get(remoteHost2.dataNodeId)
        .stream()
        .filter(info -> info.getReplicaId().getPartitionId() == specialPartitionId)
        .findFirst()
        .get();
    assertEquals("Store state is not expected", ReplicaState.STANDBY,
        specialReplicaInfo.getLocalStore().getCurrentState());
  }

  /**
   * Tests that replica tokens are set correctly and go through different stages correctly.
   * @throws InterruptedException
   */
  @Test
  public void replicaTokenTest() throws InterruptedException {
    final long tokenPersistInterval = 100;
    Time time = new MockTime();
    MockFindToken token1 = new MockFindToken(0, 0);
    RemoteReplicaInfo remoteReplicaInfo = new RemoteReplicaInfo(new MockReplicaId(), new MockReplicaId(),
        new InMemoryStore(null, Collections.emptyList(), Collections.emptyList(), null), token1, tokenPersistInterval,
        time, new Port(5000, PortType.PLAINTEXT));

    // The equality check is for the reference, which is fine.
    // Initially, the current token and the token to persist are the same.
    assertEquals(token1, remoteReplicaInfo.getToken());
    assertEquals(token1, remoteReplicaInfo.getTokenToPersist());
    MockFindToken token2 = new MockFindToken(100, 100);

    remoteReplicaInfo.initializeTokens(token2);
    // Both tokens should be the newly initialized token.
    assertEquals(token2, remoteReplicaInfo.getToken());
    assertEquals(token2, remoteReplicaInfo.getTokenToPersist());
    remoteReplicaInfo.onTokenPersisted();

    MockFindToken token3 = new MockFindToken(200, 200);

    remoteReplicaInfo.setToken(token3);
    // Token to persist should still be the old token.
    assertEquals(token3, remoteReplicaInfo.getToken());
    assertEquals(token2, remoteReplicaInfo.getTokenToPersist());
    remoteReplicaInfo.onTokenPersisted();

    // Sleep for shorter than token persist interval.
    time.sleep(tokenPersistInterval - 1);
    // Token to persist should still be the old token.
    assertEquals(token3, remoteReplicaInfo.getToken());
    assertEquals(token2, remoteReplicaInfo.getTokenToPersist());
    remoteReplicaInfo.onTokenPersisted();

    MockFindToken token4 = new MockFindToken(200, 200);
    remoteReplicaInfo.setToken(token4);

    time.sleep(2);
    // Token to persist should be the most recent token as of currentTime - tokenToPersistInterval
    // which is token3 at this time.
    assertEquals(token4, remoteReplicaInfo.getToken());
    assertEquals(token3, remoteReplicaInfo.getTokenToPersist());
    remoteReplicaInfo.onTokenPersisted();

    time.sleep(tokenPersistInterval + 1);
    // The most recently set token as of currentTime - tokenToPersistInterval is token4
    assertEquals(token4, remoteReplicaInfo.getToken());
    assertEquals(token4, remoteReplicaInfo.getTokenToPersist());
    remoteReplicaInfo.onTokenPersisted();
  }

  // helpers

  /**
   * Verify remote replica info is/isn't present in given {@link PartitionInfo}.
   * @param partitionInfo the {@link PartitionInfo} to check if it contains remote replica info
   * @param remoteReplica remote replica to check
   * @param shouldExist if {@code true}, remote replica info should exist. {@code false} otherwise
   */
  private void verifyRemoteReplicaInfo(PartitionInfo partitionInfo, ReplicaId remoteReplica, boolean shouldExist) {
    Optional<RemoteReplicaInfo> findResult =
        partitionInfo.getRemoteReplicaInfos().stream().filter(info -> info.getReplicaId() == remoteReplica).findAny();
    if (shouldExist) {
      assertTrue("Expected remote replica info is not found in partition info", findResult.isPresent());
      assertEquals("Node of remote replica is not expected", remoteReplica.getDataNodeId(),
          findResult.get().getReplicaId().getDataNodeId());
    } else {
      assertFalse("Remote replica info should no long exist in partition info", findResult.isPresent());
    }
  }

  private ReplicaId getNewReplicaToAdd(MockClusterMap clusterMap) {
    DataNodeId currentNode = clusterMap.getDataNodeIds().get(0);
    PartitionId newPartition = clusterMap.createNewPartition(clusterMap.getDataNodes());
    return newPartition.getReplicaIds()
        .stream()
        .filter(r -> ((ReplicaId) r).getDataNodeId() == currentNode)
        .findFirst()
        .get();
  }

  /**
   * Helper method to create storage manager and replication manager
   * @param clusterMap {@link ClusterMap} to use
   * @param clusterMapConfig {@link ClusterMapConfig} to use
   * @param clusterParticipant {@link com.github.ambry.clustermap.ClusterParticipant} for listener registration.
   * @return a pair of storage manager and replication manager
   * @throws Exception
   */
  private Pair<StorageManager, ReplicationManager> createStorageManagerAndReplicationManager(ClusterMap clusterMap,
      ClusterMapConfig clusterMapConfig, MockHelixParticipant clusterParticipant) throws Exception {
    StoreConfig storeConfig = new StoreConfig(verifiableProperties);
    DataNodeId dataNodeId = clusterMap.getDataNodeIds().get(0);
    MockStoreKeyConverterFactory storeKeyConverterFactory = new MockStoreKeyConverterFactory(null, null);
    storeKeyConverterFactory.setConversionMap(new HashMap<>());
    StorageManager storageManager =
        new StorageManager(storeConfig, new DiskManagerConfig(verifiableProperties), Utils.newScheduler(1, true),
            new MetricRegistry(), null, clusterMap, dataNodeId, null, clusterParticipant, new MockTime(), null);
    storageManager.start();
    MockReplicationManager replicationManager =
        new MockReplicationManager(replicationConfig, clusterMapConfig, storeConfig, storageManager, clusterMap,
            dataNodeId, storeKeyConverterFactory, clusterParticipant);
    return new Pair<>(storageManager, replicationManager);
  }

  /**
   * Creates and gets the remote replicas that the local host will deal with and the {@link ReplicaThread} to perform
   * replication with.
   * @param batchSize the number of messages to be returned in each iteration of replication
   * @param clusterMap the {@link ClusterMap} to use
   * @param localHost the local {@link MockHost} (the one running the replica thread)
   * @param remoteHost the remote {@link MockHost} (the target of replication)
   * @param storeKeyConverter the {@link StoreKeyConverter} to be used in {@link ReplicaThread}
   * @param transformer the {@link Transformer} to be used in {@link ReplicaThread}
   * @param listener the {@link StoreEventListener} to use.
   * @param replicaSyncUpManager the {@link ReplicaSyncUpManager} to help create replica thread
   * @return a pair whose first element is the set of remote replicas and the second element is the {@link ReplicaThread}
   */
  private Pair<Map<DataNodeId, List<RemoteReplicaInfo>>, ReplicaThread> getRemoteReplicasAndReplicaThread(int batchSize,
      ClusterMap clusterMap, MockHost localHost, MockHost remoteHost, StoreKeyConverter storeKeyConverter,
      Transformer transformer, StoreEventListener listener, ReplicaSyncUpManager replicaSyncUpManager)
      throws ReflectiveOperationException {
    ReplicationMetrics replicationMetrics =
        new ReplicationMetrics(new MetricRegistry(), clusterMap.getReplicaIds(localHost.dataNodeId));
    replicationMetrics.populateSingleColoMetrics(remoteHost.dataNodeId.getDatacenterName());
    List<RemoteReplicaInfo> remoteReplicaInfoList = localHost.getRemoteReplicaInfos(remoteHost, listener);
    Map<DataNodeId, List<RemoteReplicaInfo>> replicasToReplicate =
        Collections.singletonMap(remoteHost.dataNodeId, remoteReplicaInfoList);
    StoreKeyFactory storeKeyFactory = Utils.getObj("com.github.ambry.commons.BlobIdFactory", clusterMap);
    Map<DataNodeId, MockHost> hosts = new HashMap<>();
    hosts.put(remoteHost.dataNodeId, remoteHost);
    MockConnectionPool connectionPool = new MockConnectionPool(hosts, clusterMap, batchSize);
    ReplicaThread replicaThread =
        new ReplicaThread("threadtest", new MockFindTokenHelper(storeKeyFactory, replicationConfig), clusterMap,
            new AtomicInteger(0), localHost.dataNodeId, connectionPool, replicationConfig, replicationMetrics, null,
            storeKeyConverter, transformer, clusterMap.getMetricRegistry(), false,
            localHost.dataNodeId.getDatacenterName(), new ResponseHandler(clusterMap), time, replicaSyncUpManager);
    for (RemoteReplicaInfo remoteReplicaInfo : remoteReplicaInfoList) {
      replicaThread.addRemoteReplicaInfo(remoteReplicaInfo);
    }
    for (PartitionId partitionId : clusterMap.getAllPartitionIds(null)) {
      replicationMetrics.addLagMetricForPartition(partitionId);
    }
    return new Pair<>(replicasToReplicate, replicaThread);
  }

  /**
   * Asserts the number of missing keys between the local and remote replicas and fixes the keys
   * @param expectedIndex initial expected index
   * @param expectedIndexInc increment level for the expected index (how much the findToken index is expected to increment)
   * @param batchSize how large of a batch size the internal MockConnection will use for fixing missing store keys
   * @param expectedMissingKeysSum the number of missing keys expected
   * @param replicaThread replicaThread that will be performing replication
   * @param remoteHost the remote host that keys are being pulled from
   * @param replicasToReplicate list of replicas to replicate between
   * @return expectedIndex + expectedIndexInc
   * @throws Exception
   */
  private int assertMissingKeysAndFixMissingStoreKeys(int expectedIndex, int expectedIndexInc, int batchSize,
      int expectedMissingKeysSum, ReplicaThread replicaThread, MockHost remoteHost,
      Map<DataNodeId, List<RemoteReplicaInfo>> replicasToReplicate) throws Exception {
    return assertMissingKeysAndFixMissingStoreKeys(expectedIndex, expectedIndex, expectedIndexInc, batchSize,
        expectedMissingKeysSum, replicaThread, remoteHost, replicasToReplicate);
  }

  /**
   * Asserts the number of missing keys between the local and remote replicas and fixes the keys
   * @param expectedIndex initial expected index for even numbered partitions
   * @param expectedIndexOdd initial expected index for odd numbered partitions
   * @param expectedIndexInc increment level for the expected index (how much the findToken index is expected to increment)
   * @param batchSize how large of a batch size the internal MockConnection will use for fixing missing store keys
   * @param expectedMissingKeysSum the number of missing keys expected
   * @param replicaThread replicaThread that will be performing replication
   * @param remoteHost the remote host that keys are being pulled from
   * @param replicasToReplicate list of replicas to replicate between
   * @return expectedIndex + expectedIndexInc
   * @throws Exception
   */
  private int assertMissingKeysAndFixMissingStoreKeys(int expectedIndex, int expectedIndexOdd, int expectedIndexInc,
      int batchSize, int expectedMissingKeysSum, ReplicaThread replicaThread, MockHost remoteHost,
      Map<DataNodeId, List<RemoteReplicaInfo>> replicasToReplicate) throws Exception {
    expectedIndex += expectedIndexInc;
    expectedIndexOdd += expectedIndexInc;
    List<ReplicaThread.ExchangeMetadataResponse> response =
        replicaThread.exchangeMetadata(new MockConnectionPool.MockConnection(remoteHost, batchSize),
            replicasToReplicate.get(remoteHost.dataNodeId));
    assertEquals("Response should contain a response for each replica",
        replicasToReplicate.get(remoteHost.dataNodeId).size(), response.size());
    for (int i = 0; i < response.size(); i++) {
      assertEquals(expectedMissingKeysSum, response.get(i).missingStoreKeys.size());
      assertEquals(i % 2 == 0 ? expectedIndex : expectedIndexOdd,
          ((MockFindToken) response.get(i).remoteToken).getIndex());
      replicasToReplicate.get(remoteHost.dataNodeId).get(i).setToken(response.get(i).remoteToken);
    }
    replicaThread.fixMissingStoreKeys(new MockConnectionPool.MockConnection(remoteHost, batchSize),
        replicasToReplicate.get(remoteHost.dataNodeId), response);
    for (int i = 0; i < response.size(); i++) {
      assertEquals("Token should have been set correctly in fixMissingStoreKeys()", response.get(i).remoteToken,
          replicasToReplicate.get(remoteHost.dataNodeId).get(i).getToken());
    }
    return expectedIndex;
  }

  /**
   * Asserts the number of missing keys between the local and remote replicas
   * @param expectedMissingKeysSum the number of missing keys expected for each replica
   * @param batchSize how large of a batch size the internal MockConnection will use for fixing missing store keys
   * @param replicaThread replicaThread that will be performing replication
   * @param remoteHost the remote host that keys are being pulled from
   * @param replicasToReplicate list of replicas to replicate between
   * @throws Exception
   */
  private void assertMissingKeys(int[] expectedMissingKeysSum, int batchSize, ReplicaThread replicaThread,
      MockHost remoteHost, Map<DataNodeId, List<RemoteReplicaInfo>> replicasToReplicate) throws Exception {
    List<ReplicaThread.ExchangeMetadataResponse> response =
        replicaThread.exchangeMetadata(new MockConnectionPool.MockConnection(remoteHost, batchSize),
            replicasToReplicate.get(remoteHost.dataNodeId));
    assertEquals("Response should contain a response for each replica",
        replicasToReplicate.get(remoteHost.dataNodeId).size(), response.size());
    for (int i = 0; i < response.size(); i++) {
      assertEquals(expectedMissingKeysSum[i], response.get(i).missingStoreKeys.size());
    }
  }

  /**
   * Verifies that there are no more missing keys between the local and remote host and that
   * there are an expected amount of missing buffers between the remote and local host
   * @param remoteHost
   * @param localHost
   * @param replicaThread
   * @param replicasToReplicate
   * @param idsToBeIgnoredByPartition
   * @param storeKeyConverter
   * @param expectedIndex
   * @param expectedIndexOdd
   * @param expectedMissingBuffers
   * @throws Exception
   */
  private void verifyNoMoreMissingKeysAndExpectedMissingBufferCount(MockHost remoteHost, MockHost localHost,
      ReplicaThread replicaThread, Map<DataNodeId, List<RemoteReplicaInfo>> replicasToReplicate,
      Map<PartitionId, List<StoreKey>> idsToBeIgnoredByPartition, StoreKeyConverter storeKeyConverter,
      int expectedIndex, int expectedIndexOdd, int expectedMissingBuffers) throws Exception {
    // no more missing keys
    List<ReplicaThread.ExchangeMetadataResponse> response =
        replicaThread.exchangeMetadata(new MockConnectionPool.MockConnection(remoteHost, 4),
            replicasToReplicate.get(remoteHost.dataNodeId));
    assertEquals("Response should contain a response for each replica",
        replicasToReplicate.get(remoteHost.dataNodeId).size(), response.size());
    for (int i = 0; i < response.size(); i++) {
      assertEquals(0, response.get(i).missingStoreKeys.size());
      assertEquals(i % 2 == 0 ? expectedIndex : expectedIndexOdd,
          ((MockFindToken) response.get(i).remoteToken).getIndex());
    }

    Map<PartitionId, List<MessageInfo>> missingInfos =
        remoteHost.getMissingInfos(localHost.infosByPartition, storeKeyConverter);
    for (Map.Entry<PartitionId, List<MessageInfo>> entry : missingInfos.entrySet()) {
      // test that the first key has been marked deleted
      List<MessageInfo> messageInfos = localHost.infosByPartition.get(entry.getKey());
      StoreKey deletedId = messageInfos.get(0).getStoreKey();
      assertNotNull(deletedId + " should have been deleted", getMessageInfo(deletedId, messageInfos, true, false));
      Map<StoreKey, Boolean> ignoreState = new HashMap<>();
      for (StoreKey toBeIgnored : idsToBeIgnoredByPartition.get(entry.getKey())) {
        ignoreState.put(toBeIgnored, false);
      }
      for (MessageInfo messageInfo : entry.getValue()) {
        StoreKey id = messageInfo.getStoreKey();
        if (!id.equals(deletedId)) {
          assertTrue("Message should be eligible to be ignored: " + id, ignoreState.containsKey(id));
          ignoreState.put(id, true);
        }
      }
      for (Map.Entry<StoreKey, Boolean> stateInfo : ignoreState.entrySet()) {
        assertTrue(stateInfo.getKey() + " should have been ignored", stateInfo.getValue());
      }
    }
    Map<PartitionId, List<ByteBuffer>> missingBuffers = remoteHost.getMissingBuffers(localHost.buffersByPartition);
    for (Map.Entry<PartitionId, List<ByteBuffer>> entry : missingBuffers.entrySet()) {
      assertEquals(expectedMissingBuffers, entry.getValue().size());
    }
  }

  /**
   * Selects a local and remote host for replication tests that need it.
   * @param clusterMap the {@link MockClusterMap} to use.
   * @return a {@link Pair} with the first entry being the "local" host and the second, the "remote" host.
   */
  public static Pair<MockHost, MockHost> getLocalAndRemoteHosts(MockClusterMap clusterMap) {
    // to make sure we select hosts with the SPECIAL_PARTITION_CLASS, pick hosts from the replicas of that partition
    PartitionId specialPartitionId = clusterMap.getWritablePartitionIds(MockClusterMap.SPECIAL_PARTITION_CLASS).get(0);
    // these hosts have replicas of the "special" partition and all the other partitions.
    MockHost localHost = new MockHost(specialPartitionId.getReplicaIds().get(0).getDataNodeId(), clusterMap);
    MockHost remoteHost = new MockHost(specialPartitionId.getReplicaIds().get(1).getDataNodeId(), clusterMap);
    return new Pair<>(localHost, remoteHost);
  }

  /**
   * For the given partitionId, constructs put messages and adds them to the given lists.
   * @param partitionId the {@link PartitionId} to use for generating the {@link StoreKey} of the message.
   * @param hosts the list of {@link MockHost} all of which will be populated with the messages.
   * @param count the number of messages to construct and add.
   * @return the list of blobs ids that were generated.
   * @throws MessageFormatException
   * @throws IOException
   */
  private List<StoreKey> addPutMessagesToReplicasOfPartition(PartitionId partitionId, List<MockHost> hosts, int count)
      throws MessageFormatException, IOException {
    List<StoreKey> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      short accountId = Utils.getRandomShort(TestUtils.RANDOM);
      short containerId = Utils.getRandomShort(TestUtils.RANDOM);
      short blobIdVersion = CommonTestUtils.getCurrentBlobIdVersion();
      boolean toEncrypt = i % 2 == 0;
      BlobId id = new BlobId(blobIdVersion, BlobId.BlobIdType.NATIVE, ClusterMapUtils.UNKNOWN_DATACENTER_ID, accountId,
          containerId, partitionId, toEncrypt, BlobId.BlobDataType.DATACHUNK);
      ids.add(id);
      PutMsgInfoAndBuffer msgInfoAndBuffer = createPutMessage(id, accountId, containerId, toEncrypt);
      for (MockHost host : hosts) {
        host.addMessage(partitionId, msgInfoAndBuffer.messageInfo, msgInfoAndBuffer.byteBuffer.duplicate());
      }
    }
    return ids;
  }

  private BlobId generateRandomBlobId(PartitionId partitionId) {
    short accountId = Utils.getRandomShort(TestUtils.RANDOM);
    short containerId = Utils.getRandomShort(TestUtils.RANDOM);
    short blobIdVersion = CommonTestUtils.getCurrentBlobIdVersion();
    boolean toEncrypt = Utils.getRandomShort(TestUtils.RANDOM) % 2 == 0;
    return new BlobId(blobIdVersion, BlobId.BlobIdType.NATIVE, ClusterMapUtils.UNKNOWN_DATACENTER_ID, accountId,
        containerId, partitionId, toEncrypt, BlobId.BlobDataType.DATACHUNK);
  }

  private void addPutMessagesToReplicasOfPartition(List<StoreKey> ids, List<MockHost> hosts)
      throws MessageFormatException, IOException {
    List<Transformer> transformerList = new ArrayList<>();
    for (int i = 0; i < ids.size(); i++) {
      transformerList.add(null);
    }
    addPutMessagesToReplicasOfPartition(ids, transformerList, hosts);
  }

  private void addPutMessagesToReplicasOfPartition(List<StoreKey> ids, List<Transformer> transformPerId,
      List<MockHost> hosts) throws MessageFormatException, IOException {
    Iterator<Transformer> transformerIterator = transformPerId.iterator();
    for (StoreKey storeKey : ids) {
      Transformer transformer = transformerIterator.next();
      BlobId id = (BlobId) storeKey;
      PutMsgInfoAndBuffer msgInfoAndBuffer =
          createPutMessage(id, id.getAccountId(), id.getContainerId(), BlobId.isEncrypted(id.toString()));
      MessageInfo msgInfo = msgInfoAndBuffer.messageInfo;
      ByteBuffer byteBuffer = msgInfoAndBuffer.byteBuffer;
      if (transformer != null) {
        Message message = new Message(msgInfo, new ByteBufferInputStream(byteBuffer));
        TransformationOutput output = transformer.transform(message);
        assertNull(output.getException());
        message = output.getMsg();
        byteBuffer =
            ByteBuffer.wrap(Utils.readBytesFromStream(message.getStream(), (int) message.getMessageInfo().getSize()));
        msgInfo = message.getMessageInfo();
      }
      for (MockHost host : hosts) {
        host.addMessage(id.getPartition(), msgInfo, byteBuffer.duplicate());
      }
    }
  }

  /**
   * For the given partitionId, constructs delete messages and adds them to the given lists.
   * @param partitionId the {@link PartitionId} to use for generating the {@link StoreKey} of the message.
   * @param id the {@link StoreKey} to create a delete message for.
   * @param hosts the list of {@link MockHost} all of which will be populated with the messages.
   * @throws MessageFormatException
   * @throws IOException
   */
  private void addDeleteMessagesToReplicasOfPartition(PartitionId partitionId, StoreKey id, List<MockHost> hosts)
      throws MessageFormatException, IOException {
    MessageInfo putMsg = getMessageInfo(id, hosts.get(0).infosByPartition.get(partitionId), false, false);
    short aid;
    short cid;
    if (putMsg == null) {
      // the StoreKey must be a BlobId in this case (to get the account and container id)
      aid = ((BlobId) id).getAccountId();
      cid = ((BlobId) id).getContainerId();
    } else {
      aid = putMsg.getAccountId();
      cid = putMsg.getContainerId();
    }
    ByteBuffer buffer = getDeleteMessage(id, aid, cid, CONSTANT_TIME_MS);
    for (MockHost host : hosts) {
      // ok to send false for ttlUpdated
      host.addMessage(partitionId, new MessageInfo(id, buffer.remaining(), true, false, aid, cid, CONSTANT_TIME_MS),
          buffer.duplicate());
    }
  }

  /**
   * For the given partitionId, constructs a PUT messages and adds it to the given hosts.
   * @param id the {@link StoreKey} to create a put message for.
   * @param accountId the accountId of this message.
   * @param containerId the containerId of this message.
   * @param partitionId the {@link PartitionId} to use for generating the {@link StoreKey} of the message.
   * @param hosts the list of {@link MockHost} all of which will be populated with the messages.
   * @param operationTime the operation of this message.
   * @param expirationTime the expirationTime of this message.
   * @throws MessageFormatException
   * @throws IOException
   */
  public static void addPutMessagesToReplicasOfPartition(StoreKey id, short accountId, short containerId,
      PartitionId partitionId, List<MockHost> hosts, long operationTime, long expirationTime)
      throws MessageFormatException, IOException {
    short blobIdVersion = CommonTestUtils.getCurrentBlobIdVersion();
    // add a PUT message with expiration time less than VCR threshold to remote host.
    boolean toEncrypt = TestUtils.RANDOM.nextBoolean();
    ReplicationTest.PutMsgInfoAndBuffer msgInfoAndBuffer = createPutMessage(id, accountId, containerId, toEncrypt);
    for (MockHost host : hosts) {
      host.addMessage(partitionId,
          new MessageInfo(id, msgInfoAndBuffer.byteBuffer.remaining(), expirationTime, accountId, containerId,
              operationTime), msgInfoAndBuffer.byteBuffer);
    }
  }

  /**
   * For the given partitionId, constructs ttl update messages and adds them to the given lists.
   * @param partitionId the {@link PartitionId} to use for generating the {@link StoreKey} of the message.
   * @param id the {@link StoreKey} to create a ttl update message for.
   * @param hosts the list of {@link MockHost} all of which will be populated with the messages.
   * @param expirationTime
   * @throws MessageFormatException
   * @throws IOException
   */
  public static void addTtlUpdateMessagesToReplicasOfPartition(PartitionId partitionId, StoreKey id,
      List<MockHost> hosts, long expirationTime) throws MessageFormatException, IOException {
    MessageInfo putMsg = getMessageInfo(id, hosts.get(0).infosByPartition.get(partitionId), false, false);
    short aid;
    short cid;
    if (putMsg == null) {
      // the StoreKey must be a BlobId in this case (to get the account and container id)
      aid = ((BlobId) id).getAccountId();
      cid = ((BlobId) id).getContainerId();
    } else {
      aid = putMsg.getAccountId();
      cid = putMsg.getContainerId();
    }
    ByteBuffer buffer = getTtlUpdateMessage(id, aid, cid, expirationTime, CONSTANT_TIME_MS);
    for (MockHost host : hosts) {
      host.addMessage(partitionId,
          new MessageInfo(id, buffer.remaining(), false, true, expirationTime, aid, cid, CONSTANT_TIME_MS),
          buffer.duplicate());
    }
  }

  /**
   * Constructs an entire message with header, blob properties, user metadata and blob content.
   * @param id id for which the message has to be constructed.
   * @param accountId accountId of the blob
   * @param containerId containerId of the blob
   * @param enableEncryption {@code true} if encryption needs to be enabled. {@code false} otherwise
   * @return a {@link Pair} of {@link ByteBuffer} and {@link MessageInfo} representing the entire message and the
   *         associated {@link MessageInfo}
   * @throws MessageFormatException
   * @throws IOException
   */
  public static PutMsgInfoAndBuffer createPutMessage(StoreKey id, short accountId, short containerId,
      boolean enableEncryption) throws MessageFormatException, IOException {
    Random blobIdRandom = new Random(id.getID().hashCode());
    int blobSize = blobIdRandom.nextInt(500) + 501;
    int userMetadataSize = blobIdRandom.nextInt(blobSize / 2);
    int encryptionKeySize = blobIdRandom.nextInt(blobSize / 4);
    byte[] blob = new byte[blobSize];
    byte[] usermetadata = new byte[userMetadataSize];
    byte[] encryptionKey = enableEncryption ? new byte[encryptionKeySize] : null;
    blobIdRandom.nextBytes(blob);
    blobIdRandom.nextBytes(usermetadata);
    BlobProperties blobProperties =
        new BlobProperties(blobSize, "test", null, null, false, EXPIRY_TIME_MS - CONSTANT_TIME_MS, CONSTANT_TIME_MS,
            accountId, containerId, encryptionKey != null, null);
    MessageFormatInputStream stream =
        new PutMessageFormatInputStream(id, encryptionKey == null ? null : ByteBuffer.wrap(encryptionKey),
            blobProperties, ByteBuffer.wrap(usermetadata), new ByteBufferInputStream(ByteBuffer.wrap(blob)), blobSize);
    byte[] message = Utils.readBytesFromStream(stream, (int) stream.getSize());
    return new PutMsgInfoAndBuffer(ByteBuffer.wrap(message),
        new MessageInfo(id, message.length, EXPIRY_TIME_MS, accountId, containerId, CONSTANT_TIME_MS));
  }

  /**
   * Returns a delete message for the given {@code id}
   * @param id the id for which a delete message must be constructed.
   * @return {@link ByteBuffer} representing the entire message.
   * @throws MessageFormatException
   * @throws IOException
   */
  private ByteBuffer getDeleteMessage(StoreKey id, short accountId, short containerId, long deletionTimeMs)
      throws MessageFormatException, IOException {
    MessageFormatInputStream stream = new DeleteMessageFormatInputStream(id, accountId, containerId, deletionTimeMs);
    byte[] message = Utils.readBytesFromStream(stream, (int) stream.getSize());
    return ByteBuffer.wrap(message);
  }

  /**
   * Returns a TTL update message for the given {@code id}
   * @param id the id for which a ttl update message must be constructed.
   * @param accountId the account that the blob is associated with
   * @param containerId the container that the blob is associated with
   * @param expiresAtMs the new expiry time (ms)
   * @param updateTimeMs the time of the update (in ms)
   * @return {@link ByteBuffer} representing the entire message.
   * @throws MessageFormatException
   * @throws IOException
   */
  private static ByteBuffer getTtlUpdateMessage(StoreKey id, short accountId, short containerId, long expiresAtMs,
      long updateTimeMs) throws MessageFormatException, IOException {
    MessageFormatInputStream stream =
        new TtlUpdateMessageFormatInputStream(id, accountId, containerId, expiresAtMs, updateTimeMs);
    byte[] message = Utils.readBytesFromStream(stream, (int) stream.getSize());
    return ByteBuffer.wrap(message);
  }

  /**
   * Gets the {@link MessageInfo} for {@code id} if present.
   * @param id the {@link StoreKey} to look for.
   * @param messageInfos the {@link MessageInfo} list.
   * @param deleteMsg {@code true} if delete msg is requested. {@code false} otherwise
   * @param ttlUpdateMsg {@code true} if ttl update msg is requested. {@code false} otherwise
   * @return the delete {@link MessageInfo} if it exists in {@code messageInfos}. {@code null otherwise.}
   */
  static MessageInfo getMessageInfo(StoreKey id, List<MessageInfo> messageInfos, boolean deleteMsg,
      boolean ttlUpdateMsg) {
    MessageInfo toRet = null;
    for (MessageInfo messageInfo : messageInfos) {
      if (messageInfo.getStoreKey().equals(id)) {
        if (deleteMsg && messageInfo.isDeleted()) {
          toRet = messageInfo;
          break;
        } else if (ttlUpdateMsg && messageInfo.isTtlUpdated()) {
          toRet = messageInfo;
          break;
        } else if (!deleteMsg && !ttlUpdateMsg) {
          toRet = messageInfo;
          break;
        }
      }
    }
    return toRet;
  }

  /**
   * Returns a merged {@link MessageInfo} for {@code key}
   * @param key the {@link StoreKey} to look for
   * @param partitionInfos the {@link MessageInfo}s for the partition
   * @return a merged {@link MessageInfo} for {@code key}
   */
  static MessageInfo getMergedMessageInfo(StoreKey key, List<MessageInfo> partitionInfos) {
    MessageInfo info = getMessageInfo(key, partitionInfos, true, false);
    if (info == null) {
      info = getMessageInfo(key, partitionInfos, false, false);
    }
    MessageInfo ttlUpdateInfo = getMessageInfo(key, partitionInfos, false, true);
    if (ttlUpdateInfo != null) {
      info = new MessageInfo(info.getStoreKey(), info.getSize(), info.isDeleted(), true,
          ttlUpdateInfo.getExpirationTimeInMs(), info.getCrc(), info.getAccountId(), info.getContainerId(),
          info.getOperationTimeMs());
    }
    return info;
  }

  /**
   * Replicate between local and remote hosts and verify the results on local host are expected.
   * 1.remote host has different versions of blobIds and local host is empty;
   * 2.remote and local hosts have different conversion maps (StoreKeyConverter).
   * @param testSetup the {@link ReplicationTestSetup} used to provide test environment info.
   * @param expectedStr the string presenting expected sequence of PUT, DELETE messages on local host.
   * @throws Exception
   */
  private void replicateAndVerify(ReplicationTestSetup testSetup, String expectedStr) throws Exception {
    PartitionId partitionId = testSetup.partitionIds.get(0);
    List<RemoteReplicaInfo> singleReplicaList = testSetup.replicasToReplicate.get(testSetup.remoteHost.dataNodeId)
        .stream()
        .filter(e -> e.getReplicaId().getPartitionId() == partitionId)
        .collect(Collectors.toList());

    // Do the replica metadata exchange.
    List<ReplicaThread.ExchangeMetadataResponse> responses = testSetup.replicaThread.exchangeMetadata(
        new MockConnectionPool.MockConnection(testSetup.remoteHost, 10, testSetup.remoteConversionMap),
        singleReplicaList);
    // Do Get request to fix missing keys
    testSetup.replicaThread.fixMissingStoreKeys(
        new MockConnectionPool.MockConnection(testSetup.remoteHost, 10, testSetup.remoteConversionMap),
        singleReplicaList, responses);

    // Verify
    String[] expectedResults = expectedStr.equals("") ? new String[0] : expectedStr.split("\\s");
    int size = testSetup.localHost.infosByPartition.get(partitionId).size();
    assertEquals("Mismatch in number of messages on local host after replication", expectedResults.length, size);
    for (int i = 0; i < size; ++i) {
      String blobIdStr = testSetup.localHost.infosByPartition.get(partitionId).get(i).getStoreKey().toString();
      boolean isDeleteMessage = testSetup.localHost.infosByPartition.get(partitionId).get(i).isDeleted();
      switch (expectedResults[i]) {
        case "OP":
          assertEquals("Mismatch in blodId on local host after replication", testSetup.oldKey.toString(), blobIdStr);
          assertFalse("Mismatch in message type on local host after replication", isDeleteMessage);
          break;
        case "OD":
          assertEquals("Mismatch in blodId on local host after replication", testSetup.oldKey.toString(), blobIdStr);
          assertTrue("Mismatch in message type on local host after replication", isDeleteMessage);
          break;
        case "NP":
          assertEquals("Mismatch in blodId on local host after replication", testSetup.newKey.toString(), blobIdStr);
          assertFalse("Mismatch in message type on local host after replication", isDeleteMessage);
          break;
        case "ND":
          assertEquals("Mismatch in blodId on local host after replication", testSetup.newKey.toString(), blobIdStr);
          assertTrue("Mismatch in message type on local host after replication", isDeleteMessage);
          break;
      }
    }
  }

  /**
   * @return {@link StoreKeyConverter} used in replication.
   */
  private StoreKeyConverter getStoreKeyConverter() {
    MockStoreKeyConverterFactory storeKeyConverterFactory = new MockStoreKeyConverterFactory(null, null);
    storeKeyConverterFactory.setConversionMap(new HashMap<>());
    storeKeyConverterFactory.setReturnInputIfAbsent(true);
    return storeKeyConverterFactory.getStoreKeyConverter();
  }

  /**
   * Set up different combinations of PUT, DELETE messages on remote host.
   * For simplicity, we use O(old) to denote Version_2 blobId, N(new) to denote Version_5 blobId.
   * For example, OP means Version_2 PUT message, ND means Version_5 DELETE message, etc.
   * @param testSetup the {@link ReplicationTestSetup} used to provide test environment info.
   * @param msgStr the string presenting the sequence of PUT, DELETE messages
   * @throws Exception
   */
  private void createMixedMessagesOnRemoteHost(ReplicationTestSetup testSetup, String msgStr) throws Exception {
    PartitionId partitionId = testSetup.partitionIds.get(0);
    StoreKey oldKey = testSetup.oldKey;
    StoreKey newKey = testSetup.newKey;
    MockHost remoteHost = testSetup.remoteHost;
    String[] messages = msgStr.split("\\s");
    for (String message : messages) {
      switch (message) {
        case "OP":
          addPutMessagesToReplicasOfPartition(Collections.singletonList(oldKey), Collections.singletonList(remoteHost));
          break;
        case "OD":
          addDeleteMessagesToReplicasOfPartition(partitionId, oldKey, Collections.singletonList(remoteHost));
          break;
        case "NP":
          addPutMessagesToReplicasOfPartition(Collections.singletonList(newKey), Collections.singletonList(remoteHost));
          break;
        case "ND":
          addDeleteMessagesToReplicasOfPartition(partitionId, newKey, Collections.singletonList(remoteHost));
          break;
      }
    }
  }

  /**
   * Interface to help perform actions on store events.
   */
  interface StoreEventListener {
    void onPut(InMemoryStore store, List<MessageInfo> messageInfos);
  }

  /**
   * A class holds the all the needed info and configuration for replication test.
   */
  private class ReplicationTestSetup {
    MockHost localHost;
    MockHost remoteHost;
    List<PartitionId> partitionIds;
    StoreKey oldKey;
    StoreKey newKey;
    Map<DataNodeId, List<RemoteReplicaInfo>> replicasToReplicate;
    ReplicaThread replicaThread;
    Map<StoreKey, StoreKey> localConversionMap = new HashMap<>();
    Map<StoreKey, StoreKey> remoteConversionMap = new HashMap<>();

    /**
     * ReplicationTestSetup Ctor
     * @param batchSize the number of messages to be returned in each iteration of replication
     * @throws Exception
     */
    ReplicationTestSetup(int batchSize) throws Exception {
      MockClusterMap clusterMap = new MockClusterMap();
      Pair<MockHost, MockHost> localAndRemoteHosts = getLocalAndRemoteHosts(clusterMap);
      localHost = localAndRemoteHosts.getFirst();
      remoteHost = localAndRemoteHosts.getSecond();
      StoreKeyConverter storeKeyConverter = getStoreKeyConverter();
      partitionIds = clusterMap.getWritablePartitionIds(null);
      short accountId = Utils.getRandomShort(TestUtils.RANDOM);
      short containerId = Utils.getRandomShort(TestUtils.RANDOM);
      boolean toEncrypt = TestUtils.RANDOM.nextBoolean();
      oldKey =
          new BlobId(VERSION_2, BlobId.BlobIdType.NATIVE, ClusterMapUtils.UNKNOWN_DATACENTER_ID, accountId, containerId,
              partitionIds.get(0), toEncrypt, BlobId.BlobDataType.DATACHUNK);
      newKey =
          new BlobId(VERSION_5, BlobId.BlobIdType.NATIVE, ClusterMapUtils.UNKNOWN_DATACENTER_ID, accountId, containerId,
              partitionIds.get(0), toEncrypt, BlobId.BlobDataType.DATACHUNK);
      localConversionMap.put(oldKey, newKey);
      remoteConversionMap.put(newKey, oldKey);
      ((MockStoreKeyConverterFactory.MockStoreKeyConverter) storeKeyConverter).setConversionMap(localConversionMap);
      StoreKeyFactory storeKeyFactory = new BlobIdFactory(clusterMap);
      // the transformer is on local host, which should convert any Old version to New version (both id and message)
      Transformer transformer = new BlobIdTransformer(storeKeyFactory, storeKeyConverter);

      Pair<Map<DataNodeId, List<RemoteReplicaInfo>>, ReplicaThread> replicasAndThread =
          getRemoteReplicasAndReplicaThread(batchSize, clusterMap, localHost, remoteHost, storeKeyConverter,
              transformer, null, null);
      replicasToReplicate = replicasAndThread.getFirst();
      replicaThread = replicasAndThread.getSecond();
    }
  }

  /**
   * A class holds the results generated by {@link ReplicationTest#createPutMessage(StoreKey, short, short, boolean)}.
   */
  public static class PutMsgInfoAndBuffer {
    ByteBuffer byteBuffer;
    MessageInfo messageInfo;

    PutMsgInfoAndBuffer(ByteBuffer bytebuffer, MessageInfo messageInfo) {
      this.byteBuffer = bytebuffer;
      this.messageInfo = messageInfo;
    }
  }
}
