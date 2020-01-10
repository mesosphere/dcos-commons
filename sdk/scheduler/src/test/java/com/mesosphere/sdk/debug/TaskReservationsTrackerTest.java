package com.mesosphere.sdk.debug;

import com.mesosphere.sdk.offer.taskdata.TaskLabelWriter;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.testutils.*;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
//import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.*;

/**
 * This class tests the {@link TaskReservationsTracker} class
 */
public class TaskReservationsTrackerTest extends DefaultCapabilitiesTestSuite {
     
  private static final String RESERVED_RESOURCE_1_ID = "resource-1";
  private static final String RESERVED_RESOURCE_2_ID = "resource-2";
  private static final String RESERVED_RESOURCE_3_ID = "resource-3";
  private static final String RESERVED_RESOURCE_4_ID = "resource-4";
  private static final String RESERVED_RESOURCE_5_ID = "resource-5";
  private static final String RESERVED_RESOURCE_6_ID = "resource-6";
  private static final String RESERVED_RESOURCE_7_ID = "resource-7";
  private static final String RESERVED_RESOURCE_8_ID = "resource-8";

    
  private static final Protos.Resource RESERVED_RESOURCE_1 =
      ResourceTestUtils.getReservedPorts(123, 234, RESERVED_RESOURCE_1_ID);
  private static final Protos.Resource RESERVED_RESOURCE_2 =
      ResourceTestUtils.getReservedRootVolume(999.0, RESERVED_RESOURCE_2_ID, RESERVED_RESOURCE_2_ID);
  private static final Protos.Resource RESERVED_RESOURCE_3 =
      ResourceTestUtils.getReservedCpus(1.0, RESERVED_RESOURCE_3_ID);
  private static final Protos.Resource RESERVED_RESOURCE_4 =
      ResourceTestUtils.getReservedCpus(1.0, RESERVED_RESOURCE_4_ID);
  private static final Protos.Resource RESERVED_RESOURCE_5 =
      ResourceTestUtils.getReservedCpus(2.0, RESERVED_RESOURCE_5_ID);
  private static final Protos.Resource RESERVED_RESOURCE_6 =
      ResourceTestUtils.getReservedCpus(3.0, RESERVED_RESOURCE_6_ID);
  private static final Protos.Resource RESERVED_RESOURCE_7 =
      ResourceTestUtils.getReservedCpus(4.0, RESERVED_RESOURCE_7_ID);
  private static final Protos.Resource RESERVED_RESOURCE_8 =
      ResourceTestUtils.getReservedPorts(456, 456, RESERVED_RESOURCE_8_ID);
  
  private static final Protos.TaskInfo TASK_A;
  private static final Protos.TaskInfo TASK_B;
  private static final Protos.TaskInfo TASK_C;
  static {
      Protos.TaskInfo.Builder builderA = Protos.TaskInfo.newBuilder(
              TaskTestUtils.getTaskInfo(Arrays.asList(RESERVED_RESOURCE_1, RESERVED_RESOURCE_3, RESERVED_RESOURCE_5)));
      builderA.setLabels(new TaskLabelWriter(builderA)
              .setHostname(OfferTestUtils.getEmptyOfferBuilder().setHostname("host-1").build())
              .toProto())
              .setName("Task_A");
      TASK_A = builderA.build();
      
      Protos.TaskInfo.Builder builderB = Protos.TaskInfo.newBuilder(
              TaskTestUtils.getTaskInfo(Arrays.asList(RESERVED_RESOURCE_2, RESERVED_RESOURCE_4, RESERVED_RESOURCE_6)));
      builderB.setLabels(new TaskLabelWriter(builderB)
              .setHostname(OfferTestUtils.getEmptyOfferBuilder().setHostname("host-2").build())
              .toProto())
              .setName("Task_B");
      TASK_B = builderB.build();
      
      Protos.TaskInfo.Builder builderC = Protos.TaskInfo.newBuilder(
              TaskTestUtils.getTaskInfo(Arrays.asList(RESERVED_RESOURCE_7, RESERVED_RESOURCE_8)));
      builderC.setLabels(new TaskLabelWriter(builderC)
              .setHostname(OfferTestUtils.getEmptyOfferBuilder().setHostname("host-2").build())
              .toProto())
              .setName("Task_C");
      TASK_C = builderC.build();
  }

  private StateStore stateStore;

  @Before
  public void beforeEach() throws Exception {
    MockitoAnnotations.initMocks(this);

    stateStore = new StateStore(MemPersister.newBuilder().build());
    stateStore.storeTasks(Arrays.asList(TASK_A, TASK_B, TASK_C));
  }
   
  /*
   * This test is a simplified version of {@link UninstallSchedulerTest}
   */    
  //@Test
  public void testReservationTracker() {
    Map<String, Set<String>> resourceIdsByAgentHost = SchedulerUtils.getResourceIdsByAgentHost(stateStore);
    Assert.assertTrue(resourceIdsByAgentHost.keySet().containsAll(Arrays.asList("host-1", "host-2")));

    Assert.assertTrue(resourceIdsByAgentHost.get("host-1").containsAll(
        Arrays.asList(
            RESERVED_RESOURCE_1_ID,
            RESERVED_RESOURCE_3_ID,
            RESERVED_RESOURCE_5_ID)));
    Assert.assertTrue(resourceIdsByAgentHost.get("host-2").containsAll(
        Arrays.asList(
            RESERVED_RESOURCE_2_ID,
            RESERVED_RESOURCE_4_ID,
            RESERVED_RESOURCE_6_ID,
            RESERVED_RESOURCE_7_ID,
            RESERVED_RESOURCE_8_ID)));
  } 
}

