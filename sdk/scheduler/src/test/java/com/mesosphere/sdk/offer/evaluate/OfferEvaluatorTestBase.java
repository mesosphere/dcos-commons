package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.DefaultOfferRequirementProvider;
import com.mesosphere.sdk.offer.OfferRequirementProvider;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.state.DefaultStateStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Resource;
import org.junit.Before;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

/**
 * A base class for use in writing offer evaluation tests.
 */
public class OfferEvaluatorTestBase {
    protected static final SchedulerFlags flags = OfferRequirementTestUtils.getTestSchedulerFlags();

    protected OfferRequirementProvider offerRequirementProvider;
    protected StateStore stateStore;
    protected OfferEvaluator evaluator;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        stateStore = new DefaultStateStore(new MemPersister());
        offerRequirementProvider =
                new DefaultOfferRequirementProvider(stateStore, TestConstants.SERVICE_NAME, UUID.randomUUID(), flags);
        evaluator = new OfferEvaluator(stateStore, offerRequirementProvider);
    }

    protected static Label getFirstLabel(Resource resource) {
        return resource.getReservation().getLabels().getLabels(0);
    }
}
