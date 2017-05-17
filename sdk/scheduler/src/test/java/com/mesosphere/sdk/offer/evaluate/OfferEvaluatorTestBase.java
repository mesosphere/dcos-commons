package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.curator.CuratorPersister;
import com.mesosphere.sdk.curator.CuratorTestUtils;
import com.mesosphere.sdk.offer.DefaultOfferRequirementProvider;
import com.mesosphere.sdk.offer.OfferRequirementProvider;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.state.DefaultStateStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Resource;
import org.junit.Before;
import org.junit.BeforeClass;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

/**
 * A BaseTest for use in writing offer evaluation tests.
 */
public class OfferEvaluatorTestBase {
    protected static final SchedulerFlags flags = OfferRequirementTestUtils.getTestSchedulerFlags();

    static TestingServer testZk; // Findbugs wants this to be package-protected for some reason

    protected OfferRequirementProvider offerRequirementProvider;
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
        stateStore = new DefaultStateStore(
                CuratorPersister.newBuilder(TestConstants.SERVICE_NAME, testZk.getConnectString()).build());
        offerRequirementProvider =
                new DefaultOfferRequirementProvider(stateStore, TestConstants.SERVICE_NAME, UUID.randomUUID(), flags);
        evaluator = new OfferEvaluator(stateStore, offerRequirementProvider);
    }

    protected static Label getFirstLabel(Resource resource) {
        return resource.getReservation().getLabels().getLabels(0);
    }
}
