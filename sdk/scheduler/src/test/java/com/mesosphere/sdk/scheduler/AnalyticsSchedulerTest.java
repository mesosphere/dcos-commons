package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.dcos.Capabilities;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.PersisterCache;
import com.mesosphere.sdk.testutils.PlanTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

import static org.mockito.Mockito.when;

public class AnalyticsSchedulerTest extends DefaultSchedulerTest {

    private AnalyticsScheduler scheduler;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mockSchedulerFlags.isStateCacheEnabled()).thenReturn(true);
        ServiceSpec serviceSpec = getServiceSpec(podA, podB);
        stateStore = new StateStore(new PersisterCache(new MemPersister()));
        configStore = new ConfigStore<>(
                DefaultServiceSpec.getConfigurationFactory(serviceSpec), new MemPersister());
        Capabilities.overrideCapabilities(getCapabilitiesWithDefaultGpuSupport());
        scheduler = AnalyticsScheduler.newBuilder(serviceSpec, flags, new MemPersister())
               .setStateStore(stateStore)
               .setConfigStore(configStore)
               .build();
        scheduler = new TestScheduler(scheduler, true);
        register();
    }

    private static class TestScheduler extends AnalyticsScheduler {
        private final boolean apiServerReady;

        public TestScheduler(AnalyticsScheduler scheduler, boolean apiServerReady) {
            super(
                    scheduler.serviceSpec,
                    flags,
                    scheduler.resources,
                    scheduler.plans,
                    scheduler.stateStore,
                    scheduler.configStore,
                    scheduler.customEndpointProducers,
                    scheduler.recoveryPlanOverriderFactory);
            this.apiServerReady = apiServerReady;
        }

        public boolean apiServerReady() {
            return apiServerReady;
        }


    }

    @Test
    public void testConstruction() {
        Assert.assertNotNull(scheduler);
    }



    private void register() {
        scheduler.registered(mockSchedulerDriver, TestConstants.FRAMEWORK_ID, TestConstants.MASTER_INFO);
    }

}
