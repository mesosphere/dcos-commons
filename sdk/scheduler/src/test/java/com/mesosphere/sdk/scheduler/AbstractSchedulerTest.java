package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.framework.Driver;
import com.mesosphere.sdk.http.types.EndpointProducer;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.SchedulerDriver;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

/**
 * Tests for {@link AbstractScheduler}.
 */
public class AbstractSchedulerTest {

    private StateStore stateStore;

    @Mock private ConfigStore<ServiceSpec> mockConfigStore;
    @Mock private ServiceSpec mockServiceSpec;
    @Mock private SchedulerDriver mockSchedulerDriver;
    @Mock private SecretsClient mockSecretsClient;
    @Mock private PlanCoordinator mockPlanCoordinator;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        Driver.setDriver(mockSchedulerDriver);

        stateStore = new StateStore(MemPersister.newBuilder().build());
    }

    @Test
    public void testOffersDuringReconciliation() throws PersisterException {
        // Force the scheduler into an unreconciled state by adding an example task:
        stateStore.storeTasks(Collections.singleton(TestConstants.TASK_INFO));
        stateStore.storeStatus(TestConstants.TASK_NAME, TestConstants.TASK_STATUS);

        AbstractScheduler scheduler = getScheduler();

        // Not reconciled yet:
        Assert.assertEquals(MesosEventClient.OfferResponse.Result.NOT_READY,
                scheduler.offers(Arrays.asList(getOffer(), getOffer(), getOffer())).result);

        // Get the task marked reconciled:
        scheduler.taskStatus(TestConstants.TASK_STATUS);

        // Ready to go:
        Assert.assertEquals(MesosEventClient.OfferResponse.Result.PROCESSED,
                scheduler.offers(Arrays.asList(getOffer(), getOffer(), getOffer())).result);
    }

    private static Protos.Offer getOffer() {
        return getOffer(UUID.randomUUID().toString());
    }

    private static Protos.Offer getOffer(String id) {
        return Protos.Offer.newBuilder()
                .setId(Protos.OfferID.newBuilder().setValue(id))
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME)
                .build();
    }

    private TestScheduler getScheduler() {
        TestScheduler scheduler = new TestScheduler(stateStore);
        // Start and register.
        scheduler.registered(false);
        return scheduler;
    }

    private class TestScheduler extends AbstractScheduler {

        protected TestScheduler(StateStore stateStore) {
            super(
                    mockServiceSpec,
                    SchedulerConfigTestUtils.getTestSchedulerConfig(),
                    stateStore,
                    null,
                    Optional.empty(),
                    Optional.empty());
            when(mockPlanCoordinator.getPlanManagers()).thenReturn(Collections.emptyList());
            when(mockPlanCoordinator.getCandidates()).thenReturn(Collections.emptyList());
        }

        @Override
        public Collection<Object> getHTTPEndpoints() {
            return Collections.emptyList();
        }

        @Override
        public PlanCoordinator getPlanCoordinator() {
            return mockPlanCoordinator;
        }

        @Override
        public Map<String, EndpointProducer> getCustomEndpoints() {
            return Collections.emptyMap();
        }

        @Override
        public ConfigStore<ServiceSpec> getConfigStore() {
            return mockConfigStore;
        }

        @Override
        protected void registeredWithMesos() {
            // Intentionally empty.
        }

        @Override
        public void unregistered() {
            // Intentionally empty.
        }

        @Override
        protected ClientStatusResponse getStatus() {
            return ClientStatusResponse.launching(false);
        }

        @Override
        protected OfferResponse processOffers(Collection<Protos.Offer> offers, Collection<Step> steps) {
            return OfferResponse.processed(Collections.emptyList());
        }

        @Override
        protected void processStatusUpdate(Protos.TaskStatus status) throws Exception {
            String taskName = StateStoreUtils.fetchTaskInfo(stateStore, status).getName();
            stateStore.storeStatus(taskName, status);
        }

        @Override
        public UnexpectedResourcesResponse getUnexpectedResources(Collection<Offer> unusedOffers) {
            return UnexpectedResourcesResponse.processed(Collections.emptyList());
        }
    }
}
