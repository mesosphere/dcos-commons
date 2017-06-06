package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.*;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.state.DefaultStateStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
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
public class OfferEvaluatorTestBase {
    protected static final SchedulerFlags flags = OfferRequirementTestUtils.getTestSchedulerFlags();
    protected static final String ROOT_ZK_PATH = "/test-root-path";
    protected StateStore stateStore;
    protected OfferEvaluator evaluator;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        stateStore = new DefaultStateStore(new MemPersister());
        evaluator = new OfferEvaluator(stateStore, TestConstants.SERVICE_NAME, UUID.randomUUID(), flags);
    }

    protected static String getFirstResourceId(List<Resource> resources) {
        return ResourceCollectionUtils.getResourceId(resources.get(0)).get();
    }

    protected List<Resource> recordLaunchWithOfferedResources(
            PodInstanceRequirement podInstanceRequirement, Resource... offeredResources)
            throws InvalidRequirementException {
        List<OfferRecommendation> recommendations = evaluator.evaluate(
                podInstanceRequirement, Arrays.asList(OfferTestUtils.getOffer(Arrays.asList(offeredResources))));

        List<Resource> reservedResources = new ArrayList<>();
        for (OfferRecommendation recommendation : recommendations) {
            if (recommendation instanceof ReserveOfferRecommendation) {
                reservedResources.addAll(recommendation.getOperation().getReserve().getResourcesList());
            } else if (recommendation instanceof LaunchOfferRecommendation) {
                // DO NOT extract the TaskInfo from the Operation. That version has a packed CommandInfo.
                stateStore.storeTasks(Arrays.asList(((LaunchOfferRecommendation) recommendation).getTaskInfo()));
            }
        }

        return reservedResources;
    }

    protected String getResourceId(Resource resource) {
        return ResourceCollectionUtils.getResourceId(resource).get();
    }

    protected String getPrincipal(Resource resource) {
        return ResourceCollectionUtils.getPrincipal(resource).get();
    }
}
