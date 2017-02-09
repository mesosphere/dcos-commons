package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.curator.CuratorStateStore;
import com.mesosphere.sdk.offer.DefaultOfferRequirementProvider;
import com.mesosphere.sdk.offer.OfferRequirementProvider;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.CuratorTestUtils;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Resource;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

/**
 * A BaseTest for use in writing offer evaluation tests.
 */
public class OfferEvaluatorTestBase {
    public static final EnvironmentVariables ENVIRONMENT_VARIABLES =
            OfferRequirementTestUtils.getOfferRequirementProviderEnvironment();

    protected static final String ROOT_ZK_PATH = "/test-root-path";
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
        stateStore = new CuratorStateStore(ROOT_ZK_PATH, testZk.getConnectString());
        offerRequirementProvider =
                new DefaultOfferRequirementProvider(stateStore, TestConstants.SERVICE_NAME, UUID.randomUUID());
        evaluator = new OfferEvaluator(stateStore, offerRequirementProvider);
    }

    protected static Label getFirstLabel(Resource resource) {
        return resource.getReservation().getLabels().getLabels(0);
    }
}
