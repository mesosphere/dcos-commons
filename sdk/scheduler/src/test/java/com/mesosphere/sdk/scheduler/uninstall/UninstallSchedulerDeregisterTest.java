package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;

import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.time.Duration;

public class UninstallSchedulerDeregisterTest {

    private UninstallScheduler uninstallScheduler;
    @Mock
    private ConfigStore<ServiceSpec> configStore;

    @Before
    public void beforeEach() throws Exception {
        // No framework ID is set yet, and there are no tasks, and no SchedulerDriver
        uninstallScheduler = new UninstallScheduler(
                TestConstants.SERVICE_NAME,
                0,
                Duration.ofSeconds(1),
                new StateStore(new MemPersister()),
                configStore,
                OfferRequirementTestUtils.getTestSchedulerFlags());
    }

    @Test
    public void testAllButDeregisteredPlanCompletes() throws Exception {
        // Returns a simple placeholder plan with status COMPLETE
        assert uninstallScheduler.uninstallPlanManager.getPlan().isComplete();
    }

}
