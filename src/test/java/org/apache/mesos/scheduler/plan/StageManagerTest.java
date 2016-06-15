package org.apache.mesos.scheduler.plan;

import org.apache.mesos.Protos;
import org.apache.mesos.protobuf.TaskStatusBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.UUID;

/**
 * This class tests the DefaultStageManager
 */
public class StageManagerTest {

    private static String testTaskId = "test-task-id";
    private Stage stage;
    private PhaseStrategyFactory stratFactory;
    private StageManager stageManager;

    @Mock
    Block mockBlock;

    @Before
    public void beforeEach() {
        stage = getTestStage();
        stratFactory = new DefaultStrategyFactory();
        stageManager = new DefaultStageManager(stage, stratFactory);
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testSetStage() {
        Assert.assertEquals(2, stageManager.getStage().getPhases().size());
        stageManager.setStage(getEmptyStage());
        Assert.assertEquals(0, stageManager.getStage().getPhases().size());
        stageManager.setStage(getTestStage());
        Assert.assertEquals(2, stageManager.getStage().getPhases().size());
    }

    @Test
    public void testGetCurrentPhase() {
        Phase firstPhase = stage.getPhases().get(0);
        Phase secondPhase = stage.getPhases().get(1);
        Assert.assertEquals(firstPhase, stageManager.getCurrentPhase());

        completePhase(firstPhase);
        Assert.assertEquals(secondPhase, stageManager.getCurrentPhase());

        completePhase(secondPhase);
        Assert.assertNull(stageManager.getCurrentPhase());
    }

    @Test
    public void testGetPhaseStatus() {
        Phase firstPhase = stage.getPhases().get(0);
        Assert.assertEquals(Status.Pending, stageManager.getPhaseStatus(firstPhase.getId()));
        firstPhase.getBlock(0).setStatus(Status.InProgress);
        Assert.assertEquals(Status.InProgress, stageManager.getPhaseStatus(firstPhase.getId()));
        firstPhase.getBlock(0).setStatus(Status.Complete);
        Assert.assertEquals(Status.Complete, stageManager.getPhaseStatus(firstPhase.getId()));
    }


    @Test
    public void testEmptyStageStatus() {
        StageManager emptyManager = new DefaultStageManager(getEmptyStage(), stratFactory);
        Assert.assertEquals(Status.Complete, emptyManager.getStatus());
    }

    @Test
    public void testGetStatus() {
        Assert.assertEquals(Status.Pending, stageManager.getStatus());
        stage.getPhases().get(0).getBlock(0).setStatus(Status.Waiting);
        Assert.assertEquals(Status.Waiting, stageManager.getStatus());
        stage.getPhases().get(0).getBlock(0).setStatus(Status.InProgress);
        Assert.assertEquals(Status.InProgress, stageManager.getStatus());
        completePhase(stage.getPhases().get(0));
        Assert.assertEquals(Status.InProgress, stageManager.getStatus());
        completePhase(stage.getPhases().get(1));
        Assert.assertEquals(Status.Complete, stageManager.getStatus());
    }

    @Test
    public void testGetCurrentBlock() {
        Assert.assertEquals(stage.getPhases().get(0).getBlock(0), stageManager.getCurrentBlock());
    }

    @Test
    public void testIsComplete() {
        Assert.assertFalse(stageManager.isComplete());
        completePhase(stage.getPhases().get(0));
        Assert.assertFalse(stageManager.isComplete());
        completePhase(stage.getPhases().get(1));
        Assert.assertTrue(stageManager.isComplete());
    }

    @Test
    public void testInterruptProceed() {
        Assert.assertFalse(stageManager.isInterrupted());
        stageManager.interrupt();
        Assert.assertTrue(stageManager.isInterrupted());
        stageManager.proceed();
        Assert.assertFalse(stageManager.isInterrupted());
    }

    @Test
    public void testRestart() {
        Phase firstPhase = stage.getPhases().get(0);
        Block firstBlock = firstPhase.getBlock(0);
        Assert.assertTrue(firstBlock.isPending());
        firstBlock.setStatus(Status.Complete);
        Assert.assertTrue(firstBlock.isComplete());
        stageManager.restart(firstPhase.getId(), firstBlock.getId());
        Assert.assertTrue(firstBlock.isPending());
        firstBlock.setStatus(Status.InProgress);
        Assert.assertTrue(firstBlock.isInProgress());
        stageManager.restart(firstPhase.getId(), firstBlock.getId());
        Assert.assertTrue(firstBlock.isPending());
    }

    @Test
    public void testForceComplete() {
        Phase firstPhase = stage.getPhases().get(0);
        Block firstBlock = firstPhase.getBlock(0);
        Assert.assertTrue(firstBlock.isPending());
        stageManager.forceComplete(firstPhase.getId(), firstBlock.getId());
        Assert.assertTrue(firstBlock.isComplete());
        firstBlock.setStatus(Status.InProgress);
        Assert.assertTrue(firstBlock.isInProgress());
        stageManager.forceComplete(firstPhase.getId(), firstBlock.getId());
        Assert.assertTrue(firstBlock.isComplete());
    }

    @Test
    public void testUpdate() {
        when(mockBlock.getId()).thenReturn(UUID.randomUUID());

        Stage mockStage = DefaultStage.fromArgs(
                DefaultPhase.create(
                        UUID.randomUUID(),
                        "phase-0",
                        Arrays.asList(mockBlock)));
        StageManager mockStageManager = new DefaultStageManager(mockStage, stratFactory);
        Protos.TaskStatus testStatus = TaskStatusBuilder.createTaskStatus(testTaskId, Protos.TaskState.TASK_RUNNING);

        verify(mockBlock, times(0)).update(any());
        mockStageManager.update(testStatus);
        verify(mockBlock, times(1)).update(any());
    }

    @Test
    public void testUpdateObserver() {
        when(mockBlock.getId()).thenReturn(UUID.randomUUID());

        Stage mockStage = DefaultStage.fromArgs(
                DefaultPhase.create(
                        UUID.randomUUID(),
                        "phase-0",
                        Arrays.asList(mockBlock)));
        StageManager mockStageManager = new DefaultStageManager(mockStage, stratFactory);
        Protos.TaskStatus testStatus = TaskStatusBuilder.createTaskStatus(testTaskId, Protos.TaskState.TASK_RUNNING);

        verify(mockBlock, times(0)).update(any());
        mockStageManager.update(null, testStatus);
        verify(mockBlock, times(1)).update(any());
    }

    private void completePhase(Phase phase) {
        for (Block block : phase.getBlocks()) {
            block.setStatus(Status.Complete);
        }
    }

    private Stage getEmptyStage() {
        return DefaultStage.fromArgs();
    }

    private Stage getTestStage() {
        return DefaultStage.fromArgs(
                DefaultPhase.create(
                        UUID.randomUUID(),
                        "phase-0",
                        Arrays.asList(new TestBlock())),
                DefaultPhase.create(
                        UUID.randomUUID(),
                        "phase-1",
                        Arrays.asList(new TestBlock())));
    }
}
