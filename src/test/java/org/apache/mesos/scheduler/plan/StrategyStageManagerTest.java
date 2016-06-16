package org.apache.mesos.scheduler.plan;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.junit.Assert;

import java.util.Arrays;
import java.util.UUID;

/**
 * This class tests the {@link StrategyStageManager}.
 */
public class StrategyStageManagerTest {

    private TestBlock firstBlock, secondBlock;
    private Stage stage;
    private PhaseStrategyFactory stratFactory;
    private StrategyStageManager stageManager;

    @Mock
    Block mockBlock;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        firstBlock = new TestBlock();
        secondBlock = new TestBlock();
        stage = getTestStage(firstBlock, secondBlock);
        stratFactory = new StageStrategyFactory();
        stageManager = new StrategyStageManager(stage, stratFactory);
    }

    @Test
    public void testGetStatus() {
        Assert.assertEquals(Status.Waiting, stageManager.getStatus());
        stageManager.proceed();
        Assert.assertEquals(Status.Pending, stageManager.getStatus());
        firstBlock.setStatus(Status.InProgress);
        Assert.assertEquals(Status.InProgress, stageManager.getStatus());
        firstBlock.setStatus(Status.Complete);
        Assert.assertEquals(Status.Waiting, stageManager.getStatus());
    }

    private Stage getTestStage(Block firstBlock, Block secondBlock) {
        return DefaultStage.fromArgs(
                DefaultPhase.create(
                        UUID.randomUUID(),
                        "phase-0",
                        Arrays.asList(firstBlock)),
                DefaultPhase.create(
                        UUID.randomUUID(),
                        "phase-1",
                        Arrays.asList(secondBlock)));
    }
}
