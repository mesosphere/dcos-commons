package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreUtils;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.SchedulerDriver;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

/**
 * This class tests {@link SuppressReviveManager}.
 */
public class SuppressReviveManagerTest {
    private StateStore stateStore;
    private SuppressReviveManager manager;

    @Mock private SchedulerDriver driver;
    @Mock private ConfigStore<ServiceSpec> configStore;
    @Mock private PlanCoordinator planCoordinator;
    @Mock private Step step;
    @Mock private PodInstanceRequirement podInstanceRequirement;
    @Mock private PodInstance podInstance;
    @Mock private PodSpec podSpec;
    @Mock private TaskSpec taskSpec;

    @Before
    public void beforeEach() {
        stateStore = new StateStore(new MemPersister());
        manager = null;

        MockitoAnnotations.initMocks(this);
        when(podSpec.getTasks()).thenReturn(Arrays.asList(taskSpec));
        when(podSpec.getType()).thenReturn(TestConstants.POD_TYPE);
        when(taskSpec.getName()).thenReturn(TestConstants.TASK_NAME);
        when(taskSpec.getGoal()).thenReturn(GoalState.RUNNING);
        when(podInstance.getPod()).thenReturn(podSpec);
        when(podInstance.getName()).thenReturn(TestConstants.POD_TYPE + "-" + 0);
        when(podInstanceRequirement.getPodInstance()).thenReturn(podInstance);
        when(podInstanceRequirement.getTasksToLaunch()).thenReturn(Arrays.asList(TestConstants.TASK_NAME));
        when(step.getPodInstanceRequirement()).thenReturn(Optional.of(podInstanceRequirement));
    }

    @Test
    public void suppressWhenWorkIsComplete() {
        when(planCoordinator.getCandidates()).thenReturn(Collections.emptyList());
        Assert.assertFalse(StateStoreUtils.isSuppressed(stateStore));
        manager = getSuppressReviveManager(planCoordinator);
        waitSuppressed(stateStore, manager, 5);
    }

    @Test(expected = ConditionTimeoutException.class)
    public void stayRevivedWhenWorkIsIncomplete() {
        when(planCoordinator.getCandidates()).thenReturn(Arrays.asList(step));
        Assert.assertFalse(StateStoreUtils.isSuppressed(stateStore));
        manager = getSuppressReviveManager(planCoordinator);
        waitSuppressed(stateStore, manager, 5);
    }

    @Test
    public void suppressToRevivedWhenNewWorkAppears() {
        when(planCoordinator.getCandidates()).thenReturn(Collections.emptyList());
        Assert.assertFalse(StateStoreUtils.isSuppressed(stateStore));
        manager = getSuppressReviveManager(planCoordinator);
        waitSuppressed(stateStore, manager, 5);

        when(planCoordinator.getCandidates()).thenReturn(Arrays.asList(step));
        waitRevived(stateStore, manager, 5);
    }

    @Test
    public void revivedWhenNewWorkAppears() {
        when(planCoordinator.getCandidates()).thenReturn(Arrays.asList(step));
        Assert.assertFalse(StateStoreUtils.isSuppressed(stateStore));
        manager = getSuppressReviveManager(planCoordinator);

        // Create a Step with a PodInstanceRequirement that fails equality with the original mock
        PodInstanceRequirement podInstanceRequirement = mock(PodInstanceRequirement.class);
        when(podInstanceRequirement.getPodInstance()).thenReturn(podInstance);
        Step step = mock(Step.class);
        when(step.getPodInstanceRequirement()).thenReturn(Optional.of(podInstanceRequirement));
        when(planCoordinator.getCandidates()).thenReturn(Arrays.asList(step));

        verify(driver, timeout(5000).atLeastOnce()).reviveOffers();
    }

    private SuppressReviveManager getSuppressReviveManager(PlanCoordinator planCoordinator) {
        return new SuppressReviveManager(
                driver,
                stateStore,
                planCoordinator,
                0,
                1);
    }


    private static void waitSuppressed(StateStore stateStore, SuppressReviveManager reviveManager, int seconds) {
        waitStateStore(stateStore, true, seconds);
    }

    private static void waitRevived(StateStore stateStore, SuppressReviveManager reviveManager, int seconds) {
        waitStateStore(stateStore, false, seconds);
    }

    private static void waitStateStore(StateStore stateStore, boolean suppressed, int seconds) {
        Awaitility.await()
                .atMost(seconds, TimeUnit.SECONDS)
                .until(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return StateStoreUtils.isSuppressed(stateStore) == suppressed;
                    }
                });
    }
}
