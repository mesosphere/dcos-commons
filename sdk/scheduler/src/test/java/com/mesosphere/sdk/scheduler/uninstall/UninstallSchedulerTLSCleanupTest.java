package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.dcos.SecretsClient;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.testutils.*;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

public class UninstallSchedulerTLSCleanupTest extends DefaultCapabilitiesTestSuite {

    private StateStore stateStore;
    private UninstallScheduler uninstallScheduler;
    @Mock private ConfigStore<ServiceSpec> configStore;
    @Mock private SecretsClient secretsClientMock;
    @Mock private SchedulerDriver mockSchedulerDriver;

    private static final String RESERVED_RESOURCE_1_ID = "reserved-resource-id";
    private static final Protos.Resource RESERVED_RESOURCE_1 = ResourceTestUtils.getExpectedRanges(
            "ports",
            Collections.singletonList(Protos.Value.Range.newBuilder().setBegin(123).setEnd(234).build()),
            RESERVED_RESOURCE_1_ID,
            TestConstants.ROLE,
            TestConstants.PRINCIPAL);
    private static final Protos.TaskInfo TASK_A = TaskTestUtils.getTaskInfo(Arrays.asList(RESERVED_RESOURCE_1));

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        stateStore = new StateStore(new MemPersister());
        stateStore.storeTasks(Collections.singletonList(TASK_A));
        stateStore.storeFrameworkId(TestConstants.FRAMEWORK_ID);
        uninstallScheduler = new TestScheduler(
                TestConstants.SERVICE_NAME,
                0,
                Duration.ofSeconds(1),
                stateStore,
                configStore,
                Optional.of(secretsClientMock),
                true);
    }

    @Test
    public void testTLSCleanupInvoked() throws Exception {
        when(secretsClientMock.list(TestConstants.SERVICE_NAME))
                .thenReturn(Collections.emptyList());

        uninstallScheduler.registered(mockSchedulerDriver, TestConstants.FRAMEWORK_ID, TestConstants.MASTER_INFO);
        Protos.Offer offer = OfferTestUtils.getOffer(Arrays.asList(RESERVED_RESOURCE_1));
        uninstallScheduler.resourceOffers(mockSchedulerDriver, Collections.singletonList(offer));
        uninstallScheduler.awaitOffersProcessed();

        // Start the TLS cleanup phase
        uninstallScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(getOffer()));
        uninstallScheduler.awaitOffersProcessed();

        verify(secretsClientMock, times(1))
                .list(TestConstants.SERVICE_NAME);

        // Start final Deregister phase
        uninstallScheduler.resourceOffers(mockSchedulerDriver, Arrays.asList(getOffer()));
        uninstallScheduler.awaitOffersProcessed();

        assert uninstallScheduler.uninstallPlanManager.getPlan().isComplete();
    }

    private Protos.Offer getOffer() {
        return getOffer(UUID.randomUUID().toString());
    }

    private Protos.Offer getOffer(String id) {
        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(id))
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME)
                .build();
    }
    /**
     * This is an unfortunate workaround for not being able to use a Spy on the UninstallScheduler instance.
     */
    private static class TestScheduler extends UninstallScheduler {
        private final boolean apiServerReady;

        TestScheduler(
                String serviceName,
                int port,
                Duration apiServerInitTimeout,
                StateStore stateStore,
                ConfigStore<ServiceSpec> configStore,
                Optional<SecretsClient> secretsClient,
                boolean apiServerReady) {
            super(
                    serviceName,
                    port,
                    apiServerInitTimeout,
                    stateStore,
                    configStore,
                    OfferRequirementTestUtils.getTestSchedulerFlags(),
                    secretsClient);
            this.apiServerReady = apiServerReady;
        }

        @Override
        public boolean apiServerReady() {
            return apiServerReady;
        }
    }

}
