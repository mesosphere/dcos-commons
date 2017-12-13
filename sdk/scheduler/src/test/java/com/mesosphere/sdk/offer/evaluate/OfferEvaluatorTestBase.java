package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.testutils.DefaultCapabilitiesTestSuite;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import org.junit.Before;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * A base class for use in writing offer evaluation tests.
 */
public class OfferEvaluatorTestBase extends DefaultCapabilitiesTestSuite {
    protected static final SchedulerConfig SCHEDULER_CONFIG = SchedulerConfigTestUtils.getTestSchedulerConfig();

    protected StateStore stateStore;
    protected OfferEvaluator evaluator;
    protected UUID targetConfig;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        stateStore = new StateStore(new MemPersister());
        stateStore.storeFrameworkId(Protos.FrameworkID.newBuilder().setValue("framework-id").build());
        targetConfig = UUID.randomUUID();
        evaluator = new OfferEvaluator(stateStore, TestConstants.SERVICE_NAME, targetConfig, SCHEDULER_CONFIG, true);
    }

    protected void useCustomExecutor() {
        evaluator = new OfferEvaluator(stateStore, TestConstants.SERVICE_NAME, targetConfig, SCHEDULER_CONFIG, false);
    }

    protected static String getFirstResourceId(List<Resource> resources) {
        return ResourceUtils.getResourceId(resources.get(0)).get();
    }

    protected List<Resource> recordLaunchWithCompleteOfferedResources(
            PodInstanceRequirement podInstanceRequirement, Resource... offeredResources)
            throws InvalidRequirementException, IOException {
        return recordLaunchWithCompleteOfferedResources(podInstanceRequirement, Constants.ANY_ROLE, offeredResources);
    }

    protected List<Resource> recordLaunchWithCompleteOfferedResources(
            PodInstanceRequirement podInstanceRequirement, String preReservedRole, Resource... offeredResources)
            throws InvalidRequirementException, IOException {
        return recordLaunchWithOfferedResources(
                OfferTestUtils.getCompleteOffer(Arrays.asList(offeredResources), preReservedRole),
                podInstanceRequirement,
                offeredResources);
    }

    protected List<Resource> recordLaunchWithOfferedResources(
            PodInstanceRequirement podInstanceRequirement, Resource... offeredResources)
            throws InvalidRequirementException, IOException {
        return recordLaunchWithOfferedResources(
                OfferTestUtils.getOffer(Arrays.asList(offeredResources)),
                podInstanceRequirement,
                offeredResources);
    }

    private List<Resource> recordLaunchWithOfferedResources(
            Protos.Offer offer, PodInstanceRequirement podInstanceRequirement, Resource... offeredResources)
            throws InvalidRequirementException, IOException {
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement, Arrays.asList(offer));

        List<Resource> reservedResources = new ArrayList<>();
        for (OfferRecommendation recommendation : recommendations) {
            if (recommendation instanceof ReserveOfferRecommendation) {
                reservedResources.addAll(recommendation.getOperation().getReserve().getResourcesList());
            } else if (recommendation instanceof LaunchOfferRecommendation) {
                // DO NOT extract the TaskInfo from the Launch Operation. That version has a packed CommandInfo.
                stateStore.storeTasks(Arrays.asList(
                        ((LaunchOfferRecommendation) recommendation).getStoreableTaskInfo()));
            }
        }

        return reservedResources;
    }

    protected String getResourceId(Resource resource) {
        return ResourceUtils.getResourceId(resource).get();
    }

    protected String getPrincipal(Resource resource) {
        return ResourceUtils.getPrincipal(resource).get();
    }
}
