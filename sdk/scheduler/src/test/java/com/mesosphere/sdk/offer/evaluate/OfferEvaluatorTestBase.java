package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.curator.CuratorStateStore;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.LaunchOfferRecommendation;
import com.mesosphere.sdk.offer.MesosResource;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ReserveOfferRecommendation;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.CuratorTestUtils;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Resource;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * A BaseTest for use in writing offer evaluation tests.
 */
public class OfferEvaluatorTestBase {
    protected static final SchedulerFlags flags = OfferRequirementTestUtils.getTestSchedulerFlags();

    protected static final String ROOT_ZK_PATH = "/test-root-path";
    static TestingServer testZk; // Findbugs wants this to be package-protected for some reason

    protected StateStore stateStore;
    protected OfferEvaluator evaluator;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testZk = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        CuratorTestUtils.clear(testZk);
        MockitoAnnotations.initMocks(this);
        stateStore = new CuratorStateStore(ROOT_ZK_PATH, testZk.getConnectString());
        evaluator = new OfferEvaluator(stateStore, TestConstants.SERVICE_NAME, UUID.randomUUID(), flags);
    }

    protected static String getResourceId(Resource resource) {
        for (Label label : resource.getReservation().getLabels().getLabelsList()) {
            if (label.getKey().equals(MesosResource.RESOURCE_ID_KEY)) {
                return label.getValue();
            }
        }
        throw new IllegalStateException("No resource ID found in resource: " + resource);
    }

    protected static String getFirstResourceId(List<Resource> resources) {
        return getResourceId(resources.get(0));
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
}
