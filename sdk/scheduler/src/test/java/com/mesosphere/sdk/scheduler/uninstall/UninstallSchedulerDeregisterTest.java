package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.DefaultStateStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreCache;
import com.mesosphere.sdk.storage.MemPersister;

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
        StateStoreCache.resetInstanceForTests();

        StateStore stateStore = StateStoreCache.getInstance(new DefaultStateStore(new MemPersister()));

        // No framework ID is set yet, and there are no tasks, and no ScheduleDriver
        uninstallScheduler = new UninstallScheduler(0, Duration.ofSeconds(1), stateStore, configStore);
    }

    @Test
    public void testAllButDeregisteredPlanCompletes() throws Exception {
        // Returns a simple placeholder plan with status COMPLETE
        assert uninstallScheduler.uninstallPlanManager.getPlan().isComplete();
    }

}