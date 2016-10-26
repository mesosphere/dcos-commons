package org.apache.mesos.scheduler.plan;

import org.apache.commons.collections.CollectionUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.reconciliation.Reconciler;
import org.apache.mesos.scheduler.plan.strategy.SerialStrategy;
import org.apache.mesos.testutils.TestConstants;
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

    private TestBlock firstBlock, secondBlock;
    private Plan plan;
    private PlanManager planManager;

    @Mock
    Block mockBlock;

    @Mock
    Reconciler reconciler;

    @Before
    public void beforeEach() {
        firstBlock = new TestBlock();
        secondBlock = new TestBlock();
        plan = getTestPlan(firstBlock, secondBlock);
        planManager = new DefaultPlanManager(plan);
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetCurrentPhase() {
        Phase firstPhase = (Phase) plan.getChildren().get(0);
        Phase secondPhase = (Phase) plan.getChildren().get(1);
        Assert.assertEquals(firstPhase.getChildren().get(0), planManager.getCandidates(Collections.emptyList()).iterator().next());

        completePhase(firstPhase);
        Assert.assertEquals(secondPhase.getChildren().get(0), planManager.getCandidates(Collections.emptyList()).iterator().next());

        completePhase(secondPhase);
        Assert.assertTrue(CollectionUtils.isEmpty(planManager.getCandidates(Collections.emptyList())));
    }

    @Test
    public void testSetGetPhaseStatus() {
        Phase firstPhase = (Phase) plan.getChildren().get(0);
        Assert.assertEquals(Status.PENDING, firstPhase.getStatus());

        firstBlock.setStatus(Status.IN_PROGRESS);
        Assert.assertEquals(Status.IN_PROGRESS, firstPhase.getStatus());

        firstBlock.setStatus(Status.COMPLETE);
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

        firstBlock.setStatus(Status.ERROR);
        Assert.assertEquals(Status.ERROR, planManager.getPlan().getStatus());

        firstBlock.setStatus(Status.WAITING);
        Assert.assertEquals(Status.WAITING, planManager.getPlan().getStatus());

        firstBlock.setStatus(Status.IN_PROGRESS);
        Assert.assertEquals(Status.IN_PROGRESS, planManager.getPlan().getStatus());

        completePhase((Phase) plan.getChildren().get(0));
        Assert.assertEquals(Status.IN_PROGRESS, planManager.getPlan().getStatus());

        completePhase((Phase) plan.getChildren().get(1));
        Assert.assertEquals(Status.COMPLETE, planManager.getPlan().getStatus());
    }

    @Test
    public void testInProgressStatus() {
        when(reconciler.isReconciled()).thenReturn(false);
        ReconciliationPhase reconciliationPhase = ReconciliationPhase.create(reconciler);
        DefaultPhase phase0 = new DefaultPhase(
                "phase-0",
                Arrays.asList(secondBlock),
                new SerialStrategy<>(),
                Collections.emptyList());

        Plan inProgressPlan = new DefaultPlan(
                "test-plan",
                Arrays.asList(reconciliationPhase, phase0),
                new SerialStrategy<>(),
                Collections.emptyList());

        Block reconciliationBlock = (Block) reconciliationPhase.getChildren().get(0);
        reconciliationBlock.start();
        Assert.assertTrue(reconciliationBlock.isInProgress());

        PlanManager manager = new DefaultPlanManager(inProgressPlan);
        Assert.assertEquals(Status.IN_PROGRESS, manager.getPlan().getStatus());
    }

    @Test
    public void testGetCurrentBlock() {
        Collection<? extends Block> candidates = planManager.getCandidates(Arrays.asList());

        Block firstBlock = (Block) plan.getChildren().get(0).getChildren().get(0);
        Block firstCandidate = candidates.iterator().next();

        Assert.assertEquals(firstBlock, firstCandidate);
    }

    @Test
    public void testIsComplete() {
        Assert.assertFalse(planManager.getPlan().isComplete());

        completePhase((Phase) plan.getChildren().get(0));
        Assert.assertFalse(planManager.getPlan().isComplete());

        completePhase((Phase) plan.getChildren().get(1));
        Assert.assertTrue(planManager.getPlan().isComplete());
    }

    @Test
    public void testInterruptProceed() {
        Assert.assertFalse(plan.getStrategy().isInterrupted());

        plan.getStrategy().interrupt();
        Assert.assertTrue(plan.getStrategy().isInterrupted());

        plan.getStrategy().proceed();
        Assert.assertFalse(plan.getStrategy().isInterrupted());
    }

    @Test
    public void testRestart() {
        Assert.assertTrue(firstBlock.isPending());

        firstBlock.setStatus(Status.COMPLETE);
        Assert.assertTrue(firstBlock.isComplete());

        firstBlock.restart();
        Assert.assertTrue(firstBlock.isPending());

        firstBlock.setStatus(Status.IN_PROGRESS);
        Assert.assertTrue(firstBlock.isInProgress());

        firstBlock.restart();
        Assert.assertTrue(firstBlock.isPending());
    }

    @Test
    public void testForceComplete() {
        Assert.assertTrue(firstBlock.isPending());

        firstBlock.forceComplete();
        Assert.assertTrue(firstBlock.isComplete());

        firstBlock.setStatus(Status.IN_PROGRESS);
        Assert.assertTrue(firstBlock.isInProgress());

        firstBlock.forceComplete();
        Assert.assertTrue(firstBlock.isComplete());
    }

    @Test
    public void testUpdate() {
        when(mockBlock.getId()).thenReturn(UUID.randomUUID());

        DefaultPhase phase0 = new DefaultPhase(
                "phase-0",
                Arrays.asList(mockBlock),
                new SerialStrategy<>(),
                Collections.emptyList());

        DefaultPlan plan = new DefaultPlan(
                "test-plan",
                Arrays.asList(phase0),
                new SerialStrategy<>(),
                Collections.emptyList());

        verify(mockBlock, times(0)).update(any());

        plan.update(
                Protos.TaskStatus.newBuilder()
                        .setTaskId(TestConstants.TASK_ID)
                        .setState(Protos.TaskState.TASK_RUNNING)
                        .build());

        verify(mockBlock, times(1)).update(any());
    }

    @Test
    public void testAllDirtyAssets() {
        when(reconciler.isReconciled()).thenReturn(false);
        final TestBlock block1 = new TestBlock("test-block-1");
        final TestBlock block2 = new TestBlock("test-block-2");

        final DefaultPhase phase = new DefaultPhase(
                "phase-1",
                Arrays.asList(block1, block2),
                new SerialStrategy<>(),
                Collections.emptyList());

        DefaultPlan waitingPlan = new DefaultPlan(
                "test-plan",
                Arrays.asList(phase),
                new SerialStrategy<>(),
                Collections.emptyList()
        );

        block1.setStatus(Status.IN_PROGRESS);
        block2.setStatus(Status.IN_PROGRESS);

        PlanManager waitingManager = new DefaultPlanManager(waitingPlan);
        Assert.assertEquals(Status.IN_PROGRESS, waitingManager.getPlan().getStatus());

        final Set<String> dirtyAssets = waitingManager.getDirtyAssets();
        Assert.assertEquals(2, dirtyAssets.size());
        Assert.assertTrue(dirtyAssets.contains("test-block-1"));
        Assert.assertTrue(dirtyAssets.contains("test-block-2"));
    }

    @Test
    public void testOneInProgressOnePending() {
        when(reconciler.isReconciled()).thenReturn(false);
        final TestBlock block1 = new TestBlock("test-block-1");
        final TestBlock block2 = new TestBlock("test-block-2");
        final DefaultPhase phase = new DefaultPhase(
                "phase-1",
                Arrays.asList(block1, block2),
                new SerialStrategy<>(),
                Collections.emptyList());

        DefaultPlan waitingPlan = new DefaultPlan(
                "test-plan",
                Arrays.asList(phase),
                new SerialStrategy<>(),
                Collections.emptyList()
        );

        block1.setStatus(Status.PENDING);
        block2.setStatus(Status.IN_PROGRESS);

        PlanManager waitingManager = new DefaultPlanManager(waitingPlan);
        Assert.assertEquals(Status.IN_PROGRESS, waitingManager.getPlan().getStatus());

        final Set<String> dirtyAssets = waitingManager.getDirtyAssets();
        Assert.assertEquals(1, dirtyAssets.size());
        Assert.assertTrue(dirtyAssets.contains("test-block-2"));
    }

    @Test
    public void testOneInProgressOneComplete() {
        when(reconciler.isReconciled()).thenReturn(false);
        final TestBlock block1 = new TestBlock("test-block-1");
        final TestBlock block2 = new TestBlock("test-block-2");
        final DefaultPhase phase = new DefaultPhase(
                "phase-1",
                Arrays.asList(block1, block2),
                new SerialStrategy<>(),
                Collections.emptyList());

        DefaultPlan waitingPlan = new DefaultPlan(
                "test-plan",
                Arrays.asList(phase),
                new SerialStrategy<>(),
                Collections.emptyList()
        );

        block1.setStatus(Status.COMPLETE);
        block2.setStatus(Status.IN_PROGRESS);

        PlanManager waitingManager = new DefaultPlanManager(waitingPlan);
        Assert.assertEquals(Status.IN_PROGRESS, waitingManager.getPlan().getStatus());

        final Set<String> dirtyAssets = waitingManager.getDirtyAssets();
        Assert.assertEquals(1, dirtyAssets.size());
        Assert.assertTrue(dirtyAssets.contains("test-block-2"));
    }

    private static void completePhase(Phase phase) {
        phase.getChildren().forEach(block -> block.forceComplete());
    }

    private static Plan getEmptyPlan() {
        return new DefaultPlan("test-plan", Collections.emptyList(), new SerialStrategy<>(), Collections.emptyList());
    }

    private static Plan getTestPlan(Block phase0Block, Block phase1Block) {
        DefaultPhase phase0 = new DefaultPhase(
                "phase-0",
                Arrays.asList(phase0Block),
                new SerialStrategy<>(),
                Collections.emptyList());

        DefaultPhase phase1 = new DefaultPhase(
                "phase-1",
                Arrays.asList(phase1Block),
                new SerialStrategy<>(),
                Collections.emptyList());

        return new DefaultPlan(
                "test-plan",
                Arrays.asList(phase0, phase1),
                new SerialStrategy<>(),
                Collections.emptyList());
    }
}
