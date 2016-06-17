package org.apache.mesos.offer;

import static org.mockito.Mockito.*;

import java.util.*;

import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.protobuf.OfferBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;

import org.apache.mesos.state.StateStore;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ResourceCleanerTest {

  private static final String testRole = "test-role";
  private static final String testPrincipal = "test-principal";
  private static final String testResourceId = "test-resource-id";
  private static final String testContainerPath = "test-container-path";
  private static final String testPersistenceId = "test-persistence-id";
  private static final String testOfferId = "test-offer-id";
  private static final String testFrameworkId = "test-framework-id";
  private static final String testSlaveId = "test-slave-id";
  private static final String testHostname = "test-hostname";

  private static final Resource RESOURCE_1 =
          ResourceUtils.getDesiredMountVolume("role-1", "principal-1", 123, "/path1");
  private static final Resource RESOURCE_2 =
          ResourceUtils.getDesiredRootVolume("role-2", "principal-2", 234, "/path2");

  private static final TaskInfo TASK_INFO_1 = TaskInfo.newBuilder()
          .setName("task-name-1")
          .setTaskId(TaskID.newBuilder().setValue("task-id-1"))
          .setSlaveId(SlaveID.newBuilder().setValue(testSlaveId))
          .setExecutor(ExecutorInfo.newBuilder()
                  .setExecutorId(ExecutorID.newBuilder().setValue("hey"))
                  .setCommand(CommandInfo.newBuilder().build())
                  .addResources(RESOURCE_1)
                  .build())
          .build();
  private static final TaskInfo TASK_INFO_2 = TaskInfo.newBuilder()
          .setName("task-name-2")
          .setTaskId(TaskID.newBuilder().setValue("task-id-2"))
          .setSlaveId(SlaveID.newBuilder().setValue(testSlaveId))
          .addResources(RESOURCE_2)
          .build();

  @Mock private StateStore mockStateStore;
  private List<ResourceCleaner> cleaners;

  @Before
  public void beforeEach() throws Exception {
    MockitoAnnotations.initMocks(this);

    // cleaners without expected resources
    when(mockStateStore.fetchExecutorNames()).thenReturn(new ArrayList<>());
    cleaners = new ArrayList<>();
    cleaners.add(new ResourceCleaner(Collections.emptyList()));
    cleaners.add(new ResourceCleaner(mockStateStore));

    // cleaners with expected resources
    when(mockStateStore.fetchExecutorNames()).thenReturn(Arrays.asList("a", "b"));
    when(mockStateStore.fetchTasks("a")).thenReturn(Arrays.asList(TASK_INFO_1));
    when(mockStateStore.fetchTasks("b")).thenReturn(Arrays.asList(TASK_INFO_2));
    cleaners.add(new ResourceCleaner(Arrays.asList(
                    TASK_INFO_1.getExecutor().getResources(0),
                    TASK_INFO_2.getResources(0))));
    cleaners.add(new ResourceCleaner(mockStateStore));
  }

  @Test
  public void testResourcesConstructor() {
    ResourceCleaner cleaner = new ResourceCleaner(Collections.emptyList());
    Assert.assertNotNull(cleaner);
  }

  @Test
  public void testStateStoreConstructor() {
    ResourceCleaner cleaner = new ResourceCleaner(mockStateStore);
    Assert.assertNotNull(cleaner);
  }

  @Test
  public void testNoRecommendations() {
    for (ResourceCleaner cleaner : cleaners) {
      List<OfferRecommendation> recommendations = cleaner.evaluate(Collections.emptyList());

      Assert.assertNotNull(recommendations);
      Assert.assertEquals(Collections.emptyList(), recommendations);
    }
  }

  @Test
  public void testUnreserveRecommendation() {
    Resource unexpectedResource =
            ResourceBuilder.reservedCpus(1.0, testRole, testPrincipal, testResourceId);
    List<Offer> offers = getOffers(unexpectedResource);

    for (ResourceCleaner cleaner : cleaners) {
      List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

      Assert.assertEquals(1, recommendations.size());

      Operation op = recommendations.get(0).getOperation();
      Assert.assertEquals(Operation.Type.UNRESERVE, op.getType());
    }
  }

  @Test
  public void testDestroyRecommendation() {
    Resource unexpectedResource = ResourceBuilder.volume(
        1000.0,
        testRole,
        testPrincipal,
        testContainerPath,
        testPersistenceId);

    List<Offer> offers = getOffers(unexpectedResource);

    for (ResourceCleaner cleaner : cleaners) {
      List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

      Assert.assertEquals(2, recommendations.size());

      Operation destroyOp = recommendations.get(0).getOperation();
      Assert.assertEquals(Operation.Type.DESTROY, destroyOp.getType());

      Operation unreserveOp = recommendations.get(1).getOperation();
      Assert.assertEquals(Operation.Type.UNRESERVE, unreserveOp.getType());
    }
  }

  private List<Offer> getOffers(Resource resource) {
    return getOffers(Arrays.asList(resource));
  }

  private List<Offer> getOffers(List<Resource> resources) {
    OfferBuilder builder = new OfferBuilder(testOfferId, testFrameworkId, testSlaveId, testHostname);
    builder.addAllResources(resources);
    return Arrays.asList(builder.build());
  }
}
