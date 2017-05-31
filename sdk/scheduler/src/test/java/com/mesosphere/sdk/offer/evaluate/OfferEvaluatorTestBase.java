package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.state.DefaultStateStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.testutils.DefaultCapabilitiesTestSuite;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Resource;
import org.junit.Before;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * A base class for use in writing offer evaluation tests.
 */
public class OfferEvaluatorTestBase extends DefaultCapabilitiesTestSuite {
    protected static final SchedulerFlags flags = OfferRequirementTestUtils.getTestSchedulerFlags();
    protected StateStore stateStore;
    protected OfferEvaluator evaluator;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        stateStore = new DefaultStateStore(new MemPersister());
        stateStore.storeFrameworkId(Protos.FrameworkID.newBuilder().setValue("framework-id").build());
        evaluator = new OfferEvaluator(stateStore, TestConstants.SERVICE_NAME, UUID.randomUUID(), flags);
    }

    protected static String getFirstResourceId(List<Resource> resources) {
        return ResourceUtils.getResourceId(resources.get(0)).get();
    }

    protected List<Resource> recordLaunchWithOfferedResources(
            PodInstanceRequirement podInstanceRequirement, Resource... offeredResources)
            throws InvalidRequirementException {
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement, Arrays.asList(OfferTestUtils.getCompleteOffer(Arrays.asList(offeredResources))));

        List<Resource> reservedResources = new ArrayList<>();
        for (OfferRecommendation recommendation : recommendations) {
            if (recommendation instanceof ReserveOfferRecommendation) {
                reservedResources.addAll(recommendation.getOperation().getReserve().getResourcesList());
            } else if (recommendation instanceof LaunchOfferRecommendation) {
                // DO NOT extract the TaskInfo from the Operation. That version has a packed CommandInfo.
                LaunchOfferRecommendation launchOfferRecommendation = (LaunchOfferRecommendation) recommendation;
                Protos.TaskInfo taskInfo = launchOfferRecommendation.getTaskInfo().toBuilder()
                        .setExecutor(launchOfferRecommendation.getExecutorInfo()).build();
                stateStore.storeTasks(Arrays.asList(taskInfo));
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
