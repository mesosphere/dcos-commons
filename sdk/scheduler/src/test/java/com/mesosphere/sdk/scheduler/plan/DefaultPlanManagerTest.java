package com.mesosphere.sdk.scheduler.plan;

import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos;
import com.mesosphere.sdk.reconciliation.Reconciler;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.mockito.Mockito.*;

/**
 * This class tests the {@link DefaultPlanManager}.
 */
public class DefaultPlanManagerTest {

    private TestStep firstStep, secondStep;
    private Plan plan;
    private PlanManager planManager;

    @Mock Step mockStep;
    @Mock Reconciler reconciler;
    @Mock private PodInstanceRequirement podInstance0;
    @Mock private PodInstanceRequirement podInstance1;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        firstStep = new TestStep("step-0", podInstance0);
        secondStep = new TestStep("step-1", podInstance1);
        plan = getTestPlan(firstStep, secondStep);
        planManager = new DefaultPlanManager(plan);
        planManager.getPlan().proceed();
    }

    @Test
    public void testGetCurrentPhase() {
        Phase firstPhase = plan.getChildren().get(0);
        Phase secondPhase = plan.getChildren().get(1);
        Assert.assertEquals(firstPhase.getChildren().get(0), planManager.getCandidates(Collections.emptyList()).iterator().next());

        completePhase(firstPhase);
        Assert.assertEquals(secondPhase.getChildren().get(0), planManager.getCandidates(Collections.emptyList()).iterator().next());

        completePhase(secondPhase);
        Assert.assertTrue(CollectionUtils.isEmpty(planManager.getCandidates(Collections.emptyList())));
    }

    @Test
    public void testSetGetPhaseStatus() {
        Phase firstPhase = plan.getChildren().get(0);
        Assert.assertEquals(Status.PENDING, firstPhase.getStatus());

        firstStep.setStatus(Status.PREPARED);
        Assert.assertEquals(Status.IN_PROGRESS, firstPhase.getStatus());

        firstStep.setStatus(Status.COMPLETE);
        Assert.assertEquals(Status.COMPLETE, firstPhase.getStatus());
    }


    @Test
    public void testEmptyStageStatus() {
        PlanManager emptyManager = new DefaultPlanManager(getEmptyPlan());
        Assert.assertEquals(Status.COMPLETE, emptyManager.getPlan().getStatus());
    }

    @Test
    public void testGetStatus() {
        Assert.assertEquals(Status.PENDING, planManager.getPlan().getStatus());

        firstStep.setStatus(Status.ERROR);
        Assert.assertEquals(Status.ERROR, planManager.getPlan().getStatus());

        firstStep.setStatus(Status.WAITING);
        Assert.assertEquals(Status.WAITING, planManager.getPlan().getStatus());

        firstStep.setStatus(Status.PREPARED);
        Assert.assertEquals(Status.IN_PROGRESS, planManager.getPlan().getStatus());

        completePhase(plan.getChildren().get(0));
        Assert.assertEquals(Status.IN_PROGRESS, planManager.getPlan().getStatus());

        completePhase(plan.getChildren().get(1));
        Assert.assertEquals(Status.COMPLETE, planManager.getPlan().getStatus());
    }

    @Test
    public void testGetCurrentStep() {
        Collection<? extends Step> candidates = planManager.getCandidates(Arrays.asList());

        Step firstStep = plan.getChildren().get(0).getChildren().get(0);
        Step firstCandidate = candidates.iterator().next();

        Assert.assertEquals(firstStep, firstCandidate);
    }

    @Test
    public void testIsComplete() {
        Assert.assertFalse(planManager.getPlan().isComplete());

        completePhase(plan.getChildren().get(0));
        Assert.assertFalse(planManager.getPlan().isComplete());

        completePhase(plan.getChildren().get(1));
        Assert.assertTrue(planManager.getPlan().isComplete());
    }

    @Test
    public void testInterruptProceed() {
        Assert.assertFalse(plan.isInterrupted());

        plan.interrupt();
        Assert.assertTrue(plan.isInterrupted());

        plan.proceed();
        Assert.assertFalse(plan.isInterrupted());
    }

    @Test
    public void testRestart() {
        Assert.assertTrue(firstStep.isPending());

        firstStep.setStatus(Status.COMPLETE);
        Assert.assertTrue(firstStep.isComplete());

        firstStep.restart();
        Assert.assertTrue(firstStep.isPending());

        firstStep.setStatus(Status.PREPARED);
        Assert.assertTrue(firstStep.isPrepared());

        firstStep.restart();
        Assert.assertTrue(firstStep.isPending());
    }

    @Test
    public void testForceComplete() {
        Assert.assertTrue(firstStep.isPending());

        firstStep.forceComplete();
        Assert.assertTrue(firstStep.isComplete());

        firstStep.setStatus(Status.PREPARED);
        Assert.assertTrue(firstStep.isPrepared());

        firstStep.forceComplete();
        Assert.assertTrue(firstStep.isComplete());
    }

    @Test
    public void testUpdate() {
        when(mockStep.getId()).thenReturn(UUID.randomUUID());

        DefaultPhase phase0 = new DefaultPhase(
                "phase-0",
                Arrays.asList(mockStep),
                new SerialStrategy<>(),
                Collections.emptyList());

        DefaultPlan plan = new DefaultPlan(
                "test-plan",
                Arrays.asList(phase0),
                new SerialStrategy<>(),
                Collections.emptyList());

        verify(mockStep, times(0)).update(any());

        plan.update(
                Protos.TaskStatus.newBuilder()
                        .setTaskId(TestConstants.TASK_ID)
                        .setState(Protos.TaskState.TASK_RUNNING)
                        .build());

        verify(mockStep, times(1)).update(any());
    }

    @Test
    public void testUpdateParameters() {
        when(mockStep.getId()).thenReturn(UUID.randomUUID());

        DefaultPhase phase0 = new DefaultPhase(
                "phase-0",
                Arrays.asList(mockStep),
                new SerialStrategy<>(),
                Collections.emptyList());

        DefaultPlan plan = new DefaultPlan(
                "test-plan",
                Arrays.asList(phase0),
                new SerialStrategy<>(),
                Collections.emptyList());

        verify(mockStep, times(0)).update(any());

        Map<String, String> parameters = new HashMap<>();
        parameters.put("PARAM1", "value1");
        plan.updateParameters(parameters);

        verify(mockStep, times(1)).updateParameters(parameters);
    }

    @Test
    public void testAllDirtyAssets() {
        when(reconciler.isReconciled()).thenReturn(false);
        final TestStep step1 = new TestStep("test-step-0", podInstance0);
        final TestStep step2 = new TestStep("test-step-1", podInstance1);

        final DefaultPhase phase = new DefaultPhase(
                "phase-1",
                Arrays.asList(step1, step2),
                new SerialStrategy<>(),
                Collections.emptyList());

        DefaultPlan waitingPlan = new DefaultPlan(
                "test-plan",
                Arrays.asList(phase),
                new SerialStrategy<>(),
                Collections.emptyList());

        step1.setStatus(Status.PREPARED);
        step2.setStatus(Status.PREPARED);

        PlanManager waitingManager = new DefaultPlanManager(waitingPlan);
        Assert.assertEquals(Status.WAITING, waitingManager.getPlan().getStatus());
        waitingManager.getPlan().proceed();
        Assert.assertEquals(Status.IN_PROGRESS, waitingManager.getPlan().getStatus());

        final Set<PodInstanceRequirement> dirtyAssets = waitingManager.getDirtyAssets();
        Assert.assertEquals(2, dirtyAssets.size());
        Assert.assertTrue(dirtyAssets.contains(podInstance0));
        Assert.assertTrue(dirtyAssets.contains(podInstance1));
    }

    @Test
    public void testOneInProgressOnePending() {
        when(reconciler.isReconciled()).thenReturn(false);
        final TestStep step1 = new TestStep("test-step-0", podInstance0);
        final TestStep step2 = new TestStep("test-step-1", podInstance1);
        final DefaultPhase phase = new DefaultPhase(
                "phase-1",
                Arrays.asList(step1, step2),
                new SerialStrategy<>(),
                Collections.emptyList());

        DefaultPlan waitingPlan = new DefaultPlan(
                "test-plan",
                Arrays.asList(phase),
                new SerialStrategy<>(),
                Collections.emptyList());

        step1.setStatus(Status.PENDING);
        step2.setStatus(Status.PREPARED);

        PlanManager waitingManager = new DefaultPlanManager(waitingPlan);
        Assert.assertEquals(Status.WAITING, waitingManager.getPlan().getStatus());
        waitingPlan.proceed();
        Assert.assertEquals(Status.IN_PROGRESS, waitingManager.getPlan().getStatus());

        final Set<PodInstanceRequirement> dirtyAssets = waitingManager.getDirtyAssets();
        Assert.assertEquals(1, dirtyAssets.size());
        Assert.assertTrue(dirtyAssets.contains(podInstance1));
    }

    @Test
    public void testOneInProgressOneComplete() {
        when(reconciler.isReconciled()).thenReturn(false);
        final TestStep step1 = new TestStep("test-step-0", podInstance0);
        final TestStep step2 = new TestStep("test-step-1", podInstance1);
        final DefaultPhase phase = new DefaultPhase(
                "phase-1",
                Arrays.asList(step1, step2),
                new SerialStrategy<>(),
                Collections.emptyList());

        DefaultPlan waitingPlan = new DefaultPlan(
                "test-plan",
                Arrays.asList(phase),
                new SerialStrategy<>(),
                Collections.emptyList());

        step1.setStatus(Status.COMPLETE);
        step2.setStatus(Status.PREPARED);

        PlanManager waitingManager = new DefaultPlanManager(waitingPlan);

        Assert.assertFalse(phase.isInterrupted());
        Assert.assertEquals(Status.WAITING, waitingManager.getPlan().getStatus());

        final Set<PodInstanceRequirement> dirtyAssets = waitingManager.getDirtyAssets();
        Assert.assertEquals(1, dirtyAssets.size());
        Assert.assertTrue(dirtyAssets.contains(podInstance1));

        waitingManager.getPlan().proceed();
        Assert.assertEquals(Status.IN_PROGRESS, waitingManager.getPlan().getStatus());
    }

    private static void completePhase(Phase phase) {
        phase.getChildren().forEach(step -> step.forceComplete());
    }

    private static Plan getEmptyPlan() {
        return new DefaultPlan("test-plan", Collections.emptyList(), new SerialStrategy<>(), Collections.emptyList());
    }

    private static Plan getTestPlan(Step phase0Step, Step phase1Step) {
        DefaultPhase phase0 = new DefaultPhase(
                "phase-0",
                Arrays.asList(phase0Step),
                new SerialStrategy<>(),
                Collections.emptyList());

        DefaultPhase phase1 = new DefaultPhase(
                "phase-1",
                Arrays.asList(phase1Step),
                new SerialStrategy<>(),
                Collections.emptyList());

        return new DefaultPlan(
                "test-plan",
                Arrays.asList(phase0, phase1),
                new SerialStrategy<>(),
                Collections.emptyList());
    }
}
