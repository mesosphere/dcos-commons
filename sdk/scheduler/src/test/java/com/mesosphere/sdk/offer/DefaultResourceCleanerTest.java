package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.scheduler.decommission.DecommissionPlanFactory;
import com.mesosphere.sdk.state.GoalStateOverride;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.*;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Offer.Operation;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultResourceCleanerTest extends DefaultCapabilitiesTestSuite {

    private static final String EXPECTED_RESOURCE_1_ID = "expected-resource-id";
    private static final String EXPECTED_RESOURCE_2_ID = "expected-volume-id";
    private static final String UNEXPECTED_RESOURCE_1_ID = "unexpected-volume-id-1";
    private static final String UNEXPECTED_RESOURCE_2_ID = "unexpected-resource-id";
    private static final String UNEXPECTED_RESOURCE_3_ID = "unexpected-volume-id-3";


    private static final Resource EXPECTED_RESOURCE_1 =
            ResourceTestUtils.getReservedPorts(123, 234, EXPECTED_RESOURCE_1_ID);
    private static final Resource EXPECTED_RESOURCE_2 =
            ResourceTestUtils.getReservedRootVolume(999.0, EXPECTED_RESOURCE_2_ID, EXPECTED_RESOURCE_2_ID);
    private static final Resource UNEXPECTED_RESOURCE_1 =
            ResourceTestUtils.getReservedRootVolume(1000.0, UNEXPECTED_RESOURCE_1_ID, UNEXPECTED_RESOURCE_1_ID);
    private static final Resource UNEXPECTED_RESOURCE_2 =
            ResourceTestUtils.getReservedCpus(1.0, UNEXPECTED_RESOURCE_2_ID);
    private static final Resource UNEXPECTED_RESOURCE_3 =
            ResourceTestUtils.getReservedRootVolume(1001.0, UNEXPECTED_RESOURCE_3_ID, UNEXPECTED_RESOURCE_3_ID);

    private static final TaskInfo TASK_INFO_1 =
            TaskInfo.newBuilder(TaskTestUtils.getTaskInfo(Collections.emptyList()))
                    .setExecutor(TaskTestUtils.getExecutorInfo(EXPECTED_RESOURCE_1))
                    .build();
    private static final TaskInfo TASK_INFO_2 = TaskTestUtils.getTaskInfo(EXPECTED_RESOURCE_2);

    private final List<ResourceCleaner> emptyCleaners = new ArrayList<>();
    private final List<ResourceCleaner> populatedCleaners = new ArrayList<>();
    private final List<ResourceCleaner> allCleaners = new ArrayList<>();
    private final StateStore mockStateStore;

    public DefaultResourceCleanerTest() {
        // Validate ResourceCleaner statelessness by only initializing them once

        mockStateStore = mock(StateStore.class);

        // cleaners without any expected resources
        when(mockStateStore.fetchTasks()).thenReturn(new ArrayList<>());
        when(mockStateStore.fetchGoalOverrideStatus(TASK_INFO_1.getName())).thenReturn(GoalStateOverride.Status.INACTIVE);
        when(mockStateStore.fetchGoalOverrideStatus(TASK_INFO_2.getName())).thenReturn(GoalStateOverride.Status.INACTIVE);
        emptyCleaners.add(new DefaultResourceCleaner(mockStateStore));

        // cleaners with expected resources
        when(mockStateStore.fetchTasks())
                .thenReturn(Arrays.asList(TASK_INFO_1, TASK_INFO_2));
        populatedCleaners.add(new DefaultResourceCleaner(mockStateStore));

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
        List<Offer> offers = OfferTestUtils.getOffers(UNEXPECTED_RESOURCE_1);

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
        List<Offer> offers = OfferTestUtils.getOffers(UNEXPECTED_RESOURCE_2);

        for (ResourceCleaner cleaner : allCleaners) {
            List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

            assertEquals("Got: " + recommendations, 1, recommendations.size());

            assertEquals(Operation.Type.UNRESERVE, recommendations.get(0).getOperation().getType());
        }
    }

    @Test
    public void testUnexpectedMix() {
        List<Offer> offers = Arrays.asList(
                OfferTestUtils.getOffer(UNEXPECTED_RESOURCE_1),
                OfferTestUtils.getOffer(UNEXPECTED_RESOURCE_2),
                OfferTestUtils.getOffer(UNEXPECTED_RESOURCE_3));

        for (ResourceCleaner cleaner : allCleaners) {
            List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

            assertEquals("Got: " + recommendations, 5, recommendations.size());

            // all destroy operations, followed by all unreserve operations

            OfferRecommendation rec = recommendations.get(0);
            assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_1_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(1);
            assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_3_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(2);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_1_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(3);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_2_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(4);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_3_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));
        }
    }

    @Test
    public void testEmptyCleanersAllUnexpected() {
        List<Offer> offers = Arrays.asList(
                OfferTestUtils.getOffer(UNEXPECTED_RESOURCE_1),
                OfferTestUtils.getOffer(EXPECTED_RESOURCE_1),
                OfferTestUtils.getOffer(UNEXPECTED_RESOURCE_2),
                OfferTestUtils.getOffer(EXPECTED_RESOURCE_2),
                OfferTestUtils.getOffer(UNEXPECTED_RESOURCE_3));

        // These cleaners don't have any expected resources. everything above is "unexpected":
        for (ResourceCleaner cleaner : emptyCleaners) {
            List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

            assertEquals("Got: " + recommendations, 8, recommendations.size());

            // All destroy operations, followed by all unreserve operations

            OfferRecommendation rec = recommendations.get(0);
            assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_1_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(1);
            assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());
            assertEquals(EXPECTED_RESOURCE_2_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(2);
            assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_3_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(3);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_1_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(4);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(EXPECTED_RESOURCE_1_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(5);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_2_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(6);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(EXPECTED_RESOURCE_2_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(7);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_3_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));
        }
    }

    @Test
    public void testPopulatedCleanersSomeExpected() {
        List<Offer> offers = Arrays.asList(
                OfferTestUtils.getOffer(UNEXPECTED_RESOURCE_1),
                OfferTestUtils.getOffer(EXPECTED_RESOURCE_1),
                OfferTestUtils.getOffer(UNEXPECTED_RESOURCE_2),
                OfferTestUtils.getOffer(EXPECTED_RESOURCE_2),
                OfferTestUtils.getOffer(UNEXPECTED_RESOURCE_3));

        // these cleaners have expected resources populated, so they are omitted from the response:
        for (ResourceCleaner cleaner : populatedCleaners) {
            List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

            assertEquals("Got: " + recommendations, 5, recommendations.size());

            // all destroy operations, followed by all unreserve operations

            OfferRecommendation rec = recommendations.get(0);
            assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_1_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(1);
            assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_3_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(2);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_1_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(3);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_2_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));

            rec = recommendations.get(4);
            assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
            assertEquals(UNEXPECTED_RESOURCE_3_ID,
                    ResourceTestUtils.getResourceId(rec.getOffer().getResources(0)));
        }
    }

    @Test
    public void testPopulatedCleanersAllExpected() {
        List<Offer> offers = OfferTestUtils.getOffers(Arrays.asList(EXPECTED_RESOURCE_1, EXPECTED_RESOURCE_2));

        // these cleaners have expected resources populated, so they are omitted from the response:
        for (ResourceCleaner cleaner : populatedCleaners) {
            List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

            assertEquals("Got: " + recommendations, 0, recommendations.size());
        }
    }

    @Test
    public void testExpectedPermanentlyFailedResource() {
        TaskInfo failedTask = TaskTestUtils.withFailedFlag(TASK_INFO_1);
        when(mockStateStore.fetchTasks()).thenReturn(Arrays.asList(failedTask, TASK_INFO_2));
        ResourceCleaner cleaner = new DefaultResourceCleaner(mockStateStore);

        List<Offer> offers = OfferTestUtils.getOffers(EXPECTED_RESOURCE_1);
        List<OfferRecommendation> recommendations = cleaner.evaluate(offers);
        assertEquals("Got: " + recommendations, 1, recommendations.size());
        assertEquals(Operation.Type.UNRESERVE, recommendations.get(0).getOperation().getType());
    }

    @Test
    public void testExpectedPermanentlyFailedVolume() {
        TaskInfo failedTask = TaskTestUtils.withFailedFlag(TASK_INFO_2);
        when(mockStateStore.fetchTasks()).thenReturn(Arrays.asList(TASK_INFO_1, failedTask));
        ResourceCleaner cleaner = new DefaultResourceCleaner(mockStateStore);
        List<Offer> offers = OfferTestUtils.getOffers(EXPECTED_RESOURCE_2);
        List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

        assertEquals("Got: " + recommendations, 2, recommendations.size());

        OfferRecommendation rec = recommendations.get(0);
        assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());

        rec = recommendations.get(1);
        assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
    }

    @Test
    public void testExpectedDecommissioningResource() {
        when(mockStateStore.fetchGoalOverrideStatus(TASK_INFO_1.getName()))
                .thenReturn(DecommissionPlanFactory.DECOMMISSIONING_STATUS);
        ResourceCleaner cleaner = new DefaultResourceCleaner(mockStateStore);

        List<Offer> offers = OfferTestUtils.getOffers(EXPECTED_RESOURCE_1);
        List<OfferRecommendation> recommendations = cleaner.evaluate(offers);
        assertEquals("Got: " + recommendations, 1, recommendations.size());
        assertEquals(Operation.Type.UNRESERVE, recommendations.get(0).getOperation().getType());
    }

    @Test
    public void testExpectedDecommissioningVolume() {
        when(mockStateStore.fetchGoalOverrideStatus(TASK_INFO_2.getName()))
                .thenReturn(DecommissionPlanFactory.DECOMMISSIONING_STATUS);
        ResourceCleaner cleaner = new DefaultResourceCleaner(mockStateStore);
        List<Offer> offers = OfferTestUtils.getOffers(EXPECTED_RESOURCE_2);
        List<OfferRecommendation> recommendations = cleaner.evaluate(offers);

        assertEquals("Got: " + recommendations, 2, recommendations.size());

        OfferRecommendation rec = recommendations.get(0);
        assertEquals(Operation.Type.DESTROY, rec.getOperation().getType());

        rec = recommendations.get(1);
        assertEquals(Operation.Type.UNRESERVE, rec.getOperation().getType());
    }
}
