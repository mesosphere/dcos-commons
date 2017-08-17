package com.mesosphere.sdk.scheduler;

import com.google.common.eventbus.EventBus;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanManager;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.when;

/**
 * This class tests {@link SuppressReviveManager}.
 */
@RunWith(Parameterized.class)
public class SuppressReviveManagerTest {
    private StateStore stateStore;
    private EventBus eventBus = new EventBus();
    private SuppressReviveManager suppressReviveManager;

    @Mock private SchedulerDriver driver;
    @Mock private PlanManager planManager;

    @Mock private Plan completePlan;
    @Mock private Plan inprogressPlan;

    @Mock private Phase completePhase;
    @Mock private Phase inProgressPhase;

    @Parameterized.Parameters
    public static List<Object[]> data() {
        return Arrays.asList(new Object[10][0]);
    }

    public SuppressReviveManagerTest() {

    }

    @Before
    public void beforeEach() {
        stateStore = new StateStore(new MemPersister());
        suppressReviveManager = null;

        MockitoAnnotations.initMocks(this);

        when(completePhase.getStatus()).thenReturn(Status.COMPLETE);
        when(inProgressPhase.getStatus()).thenReturn(Status.IN_PROGRESS);

        when(completePlan.getName()).thenReturn("complete-plan");
        when(completePlan.getChildren()).thenReturn(Arrays.asList(completePhase));

        when(inprogressPlan.getName()).thenReturn("in-progress-plan");
        when(inprogressPlan.getChildren()).thenReturn(Arrays.asList(inProgressPhase));
    }

    @Test
    public void startWithCompletePlan() {
        when(planManager.getPlan()).thenReturn(completePlan);
        suppressReviveManager = new SuppressReviveManager(
                stateStore,
                driver,
                eventBus,
                Arrays.asList(planManager));
        suppressReviveManager.start();
        waitState(suppressReviveManager, SuppressReviveManager.State.WAITING_FOR_OFFER);
    }

    @Test
    public void startWithInProgressPlan() {
        when(planManager.getPlan()).thenReturn(inprogressPlan);
        suppressReviveManager = new SuppressReviveManager(
                stateStore,
                driver,
                eventBus,
                Arrays.asList(planManager));
        suppressReviveManager.start();
        waitState(suppressReviveManager, SuppressReviveManager.State.WAITING_FOR_OFFER);
    }

    @Test
    public void suppressWhenComplete() {
        getSuppressedManager();
    }

    @Test
    public void reviveOnTaskFailure() {
        suppressReviveManager = getSuppressedManager();
        sendFailedTaskStatus();
        Assert.assertEquals(SuppressReviveManager.State.WAITING_FOR_OFFER, suppressReviveManager.getState());
        sendOffer();
        Assert.assertEquals(SuppressReviveManager.State.REVIVED, suppressReviveManager.getState());

        // Should go back to suppressed because all plans remain complete
        waitSuppressed(stateStore, suppressReviveManager);
    }

    @Test
    public void reviveOnPlanInProgress() {
        suppressReviveManager = getSuppressedManager();
        when(planManager.getPlan()).thenReturn(inprogressPlan);
        waitState(suppressReviveManager, SuppressReviveManager.State.WAITING_FOR_OFFER);
        waitStateStore(stateStore, false);
        sendOffer();
        waitRevived(stateStore, suppressReviveManager);
    }

    private SuppressReviveManager getSuppressedManager() {
        when(planManager.getPlan()).thenReturn(completePlan);
        Assert.assertFalse(StateStoreUtils.isSuppressed(stateStore));
        SuppressReviveManager suppressReviveManager = getSuppressReviveManager(planManager);
        suppressReviveManager.start();
        waitState(suppressReviveManager, SuppressReviveManager.State.WAITING_FOR_OFFER);
        sendOffer();
        waitSuppressed(stateStore, suppressReviveManager);
        return suppressReviveManager;
    }

    private SuppressReviveManager getSuppressReviveManager(PlanManager planManager) {
        return new SuppressReviveManager(
                stateStore,
                driver,
                eventBus,
                Arrays.asList(planManager),
                0,
                1);
    }

    private static void waitState(SuppressReviveManager suppressReviveManager, SuppressReviveManager.State state) {
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return suppressReviveManager.getState().equals(state);
                    }
                });
    }

    private static void waitSuppressed(StateStore stateStore, SuppressReviveManager suppressReviveManager) {
        waitStateStore(stateStore, true);
        waitState(suppressReviveManager, SuppressReviveManager.State.SUPPRESSED);
    }

    private static void waitRevived(StateStore stateStore, SuppressReviveManager suppressReviveManager) {
        waitStateStore(stateStore, false);
        waitState(suppressReviveManager, SuppressReviveManager.State.REVIVED);
    }

    private static void waitStateStore(StateStore stateStore, boolean suppressed) {
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return StateStoreUtils.isSuppressed(stateStore) == suppressed;
                    }
                });
    }

    private void sendOffer() {
        Protos.Offer offer = Protos.Offer.newBuilder()
                .setId(TestConstants.OFFER_ID)
                .setFrameworkId(TestConstants.FRAMEWORK_ID)
                .setSlaveId(TestConstants.AGENT_ID)
                .setHostname(TestConstants.HOSTNAME)
                .build();

        eventBus.post(offer);
    }

    private void sendFailedTaskStatus() {
        sendTaskStatus(Protos.TaskState.TASK_FAILED);
    }

    private void sendTaskStatus(Protos.TaskState state) {
        Protos.TaskStatus taskStatus = Protos.TaskStatus.newBuilder()
                .setTaskId(TestConstants.TASK_ID)
                .setState(state)
                .build();

        eventBus.post(taskStatus);
    }
}
