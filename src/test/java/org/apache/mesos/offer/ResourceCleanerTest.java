package org.apache.mesos.offer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.apache.mesos.Protos.*;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.protobuf.OfferBuilder;
import org.apache.mesos.protobuf.ResourceBuilder;

import org.apache.mesos.state.StateStore;
import org.junit.Test;

public class ResourceCleanerTest {

    private static final String ROLE = "test-role";
    private static final String PRINCIPAL = "test-principal";
    private static final String EXPECTED_RESOURCE_1_ID = "expected-resource-id";
    private static final String EXPECTED_RESOURCE_2_ID = "expected-volume-id";
    private static final String UNEXPECTED_RESOURCE_1_ID = "unexpected-volume-id-1";
    private static final String UNEXPECTED_RESOURCE_2_ID = "unexpected-resource-id";
    private static final String UNEXPECTED_RESOURCE_3_ID = "unexpected-volume-id-3";
    private static final String SLAVE_ID = "test-slave-id";

    private static final Resource EXPECTED_RESOURCE_1 = ResourceBuilder.reservedPorts(
            123, 456, ROLE, PRINCIPAL, EXPECTED_RESOURCE_1_ID);
    private static final Resource EXPECTED_RESOURCE_2 = ResourceBuilder.volume(
            999.0, ROLE, PRINCIPAL, "expected-path", EXPECTED_RESOURCE_2_ID);

    private static final Resource UNEXPECTED_RESOURCE_1 = ResourceBuilder.volume(
            1000.0, ROLE, PRINCIPAL, "unexpected-path-1", UNEXPECTED_RESOURCE_1_ID);
    private static final Resource UNEXPECTED_RESOURCE_2 = ResourceBuilder.reservedCpus(
            1.0, ROLE, PRINCIPAL, UNEXPECTED_RESOURCE_2_ID);
    private static final Resource UNEXPECTED_RESOURCE_3 = ResourceBuilder.volume(
            1001.0, ROLE, PRINCIPAL, "unexpected-path-3", UNEXPECTED_RESOURCE_3_ID);

    private static final TaskInfo TASK_INFO_1 = TaskInfo.newBuilder()
            .setName("task-name-1")
            .setTaskId(TaskID.newBuilder().setValue("task-id-1"))
            .setSlaveId(SlaveID.newBuilder().setValue(SLAVE_ID))
            .setExecutor(ExecutorInfo.newBuilder()
                    .setExecutorId(ExecutorID.newBuilder().setValue("hey"))
                    .setCommand(CommandInfo.newBuilder().build())
                    .addResources(EXPECTED_RESOURCE_1)
                    .build())
            .build();
    private static final TaskInfo TASK_INFO_2 = TaskInfo.newBuilder()
            .setName("task-name-2")
            .setTaskId(TaskID.newBuilder().setValue("task-id-2"))
            .setSlaveId(SlaveID.newBuilder().setValue(SLAVE_ID))
            .addResources(EXPECTED_RESOURCE_2).build();

    private final List<ResourceCleaner> emptyCleaners = new ArrayList<>();
    private final List<ResourceCleaner> populatedCleaners = new ArrayList<>();
    private final List<ResourceCleaner> allCleaners = new ArrayList<>();

    public ResourceCleanerTest() {
        // Validate ResourceCleaner statelessness by only initializing them once

        StateStore mockStateStore = mock(StateStore.class);

        // cleaners without any expected resources
        when(mockStateStore.fetchExecutorNames()).thenReturn(new ArrayList<>());
        emptyCleaners.add(new ResourceCleaner(Collections.emptyList()));
        emptyCleaners.add(new ResourceCleaner(mockStateStore));

        // cleaners with expected resources
        populatedCleaners.add(new ResourceCleaner(Arrays.asList(
                TASK_INFO_1.getExecutor().getResources(0), TASK_INFO_2.getResources(0))));
        when(mockStateStore.fetchExecutorNames())
                .thenReturn(Arrays.asList("a", "b"));
        when(mockStateStore.fetchTasks("a"))
                .thenReturn(Arrays.asList(TASK_INFO_1));
        when(mockStateStore.fetchTasks("b"))
                .thenReturn(Arrays.asList(TASK_INFO_2));
        populatedCleaners.add(new ResourceCleaner(mockStateStore));

        allCleaners.addAll(emptyCleaners);
        allCleaners.addAll(populatedCleaners);
    }

    @Test
    public void testNoOffers() {
        for (ResourceCleaner cleaner : allCleaners) {
            List<OfferRecommendation> recommendations = cleaner.evaluate(Collections.emptyList());

            assertNotNull(recommendations);
            assertEquals(Collections.emptyList(), recommendations);
        }
    }

    @Test
    public void testUnexpectedVolume() {
        List<Offer> offers = getOffers(UNEXPECTED_RESOURCE_1);

        for (ResourceCleaner cleaner : allCleaners) {
            List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

            assertEquals("Got: " + recommendations, 2, recommendations.size());

            OfferRecommendation rec = recommendations.get(0);
            assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());

            rec = recommendations.get(1);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
        }
    }

    @Test
    public void testUnexpectedResource() {
        List<Offer> offers = getOffers(UNEXPECTED_RESOURCE_2);

        for (ResourceCleaner cleaner : allCleaners) {
            List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

            assertEquals("Got: " + recommendations, 1, recommendations.size());

            assertEquals(Operation.Type.UNRESERVE, recommendations.get(0).getOperation().getType());
        }
    }

    @Test
    public void testUnexpectedMix() {
        List<Offer> offers = getOffers(
                UNEXPECTED_RESOURCE_1, UNEXPECTED_RESOURCE_2, UNEXPECTED_RESOURCE_3);

        for (ResourceCleaner cleaner : allCleaners) {
            List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

            assertEquals("Got: " + recommendations, 5, recommendations.size());

            // all destroy operations, followed by all unreserve operations

            OfferRecommendation rec = recommendations.get(0);
            assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_1_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(1);
            assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_3_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(2);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_1_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(3);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_2_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(4);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_3_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));
        }
    }

    @Test
    public void testEmptyCleanersAllUnexpected() {
        List<Offer> offers = getOffers(
                UNEXPECTED_RESOURCE_1,
                EXPECTED_RESOURCE_1,
                UNEXPECTED_RESOURCE_2,
                EXPECTED_RESOURCE_2,
                UNEXPECTED_RESOURCE_3);

        // these cleaners don't have any expected resources. everything above is "unexpected":
        for (ResourceCleaner cleaner : emptyCleaners) {
            List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

            assertEquals("Got: " + recommendations, 8, recommendations.size());

            // all destroy operations, followed by all unreserve operations

            OfferRecommendation rec = recommendations.get(0);
            assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_1_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(1);
            assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());
            assertEquals(EXPECTED_RESOURCE_2_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(2);
            assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_3_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(3);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_1_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(4);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(EXPECTED_RESOURCE_1_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(5);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_2_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(6);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(EXPECTED_RESOURCE_2_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(7);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_3_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));
        }
    }

    @Test
    public void testPopulatedCleanersSomeExpected() {
        List<Offer> offers = getOffers(
                UNEXPECTED_RESOURCE_1,
                EXPECTED_RESOURCE_1,
                UNEXPECTED_RESOURCE_2,
                EXPECTED_RESOURCE_2,
                UNEXPECTED_RESOURCE_3);

        // these cleaners have expected resources populated, they are omitted from the response:
        for (ResourceCleaner cleaner : populatedCleaners) {
            List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

            assertEquals("Got: " + recommendations, 5, recommendations.size());

            // all destroy operations, followed by all unreserve operations

            OfferRecommendation rec = recommendations.get(0);
            assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_1_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(1);
            assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_3_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(2);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_1_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(3);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_2_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(4);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_3_ID,
                    ResourceUtils.getResourceId(rec.getOffer().getResources(0)));
        }
    }

    private static List<Offer> getOffers(Resource... resources) {
        List<Offer> offers = new ArrayList<>();
        for (Resource resource : resources) {
            offers.add(new OfferBuilder(
                    "test-offer-id", "test-framework-id", SLAVE_ID, "test-hostname")
                    .addResource(resource)
                    .build());
        }
        return offers;
    }
}
