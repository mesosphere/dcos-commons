package org.apache.mesos.scheduler.plan.strategy;

import org.apache.curator.test.TestingServer;
import org.apache.mesos.curator.CuratorStateStore;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.specification.TaskSet;
import org.apache.mesos.specification.TestTaskSetFactory;
import org.apache.mesos.state.StateStore;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.mockito.Mockito.when;

/**
 * These tests do not validate plan behavior.  See the PhaseBuilder, PlanBuilder, and Strategy tests.
 * These test serve as validation of ease of custom plan construction, similar to the CustomTaskSetTest.
 */
public class CustomPlanTest {
    @Mock Block block0;
    @Mock Block block1;
    @Mock Block block2;
    @Mock Block block3;

    private static final String SERVICE_NAME = "test-service";
    private static final String ROOT_ZK_PATH = "/test-root-path";

    private static final int TASK_COUNT = 4;
    private static final String TASK_NAME = "A";
    private static final double TASK_CPU = 1.0;
    private static final double TASK_MEM = 1000.0;
    private static final double TASK_DISK = 1500.0;
    private static final String TASK_CMD = "echo " + TASK_NAME;

    private Collection<Block> blocks;
    private ServiceSpecification serviceSpecification;
    private static TestingServer testingServer;
    private static StateStore stateStore;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testingServer = new TestingServer();
        testingServer.start();
        stateStore = new CuratorStateStore(ROOT_ZK_PATH, testingServer.getConnectString());
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        when(block0.getStrategy()).thenReturn(new SerialStrategy<>());
        when(block1.getStrategy()).thenReturn(new SerialStrategy<>());
        when(block2.getStrategy()).thenReturn(new SerialStrategy<>());
        when(block3.getStrategy()).thenReturn(new SerialStrategy<>());

        when(block0.getName()).thenReturn("block0");
        when(block1.getName()).thenReturn("block1");
        when(block2.getName()).thenReturn("block2");
        when(block3.getName()).thenReturn("block3");

        when(block0.getStatus()).thenReturn(Status.PENDING);
        when(block1.getStatus()).thenReturn(Status.PENDING);
        when(block2.getStatus()).thenReturn(Status.PENDING);
        when(block3.getStatus()).thenReturn(Status.PENDING);

        when(block0.isPending()).thenReturn(true);
        when(block1.isPending()).thenReturn(true);
        when(block2.isPending()).thenReturn(true);
        when(block3.isPending()).thenReturn(true);

        blocks = Arrays.asList(block0, block1, block2, block3);

        serviceSpecification = new ServiceSpecification() {
            @Override
            public String getName() {
                return SERVICE_NAME;
            }

            @Override
            public List<TaskSet> getTaskSets() {
                return Arrays.asList(
                        TestTaskSetFactory.getTaskSet(
                                TASK_NAME,
                                TASK_COUNT,
                                TASK_CMD,
                                TASK_CPU,
                                TASK_MEM,
                                TASK_DISK),
                        TestTaskSetFactory.getTaskSet(
                                TASK_NAME,
                                TASK_COUNT,
                                TASK_CMD,
                                TASK_CPU,
                                TASK_MEM,
                                TASK_DISK),
                        TestTaskSetFactory.getTaskSet(
                                TASK_NAME,
                                TASK_COUNT,
                                TASK_CMD,
                                TASK_CPU,
                                TASK_MEM,
                                TASK_DISK));
            }
        };
    }

    @Test
    public void testCustomPlanFromPhases() throws DependencyStrategyHelper.InvalidDependencyException {
        Phase parallelPhase = getParallelPhase();
        Phase serialPhase = getSerialPhase();
        Phase diamondPhase = getDiamondPhase();

        DefaultPlanBuilder planBuilder = new DefaultPlanBuilder("custom");
        planBuilder.addDependency(serialPhase, diamondPhase);
        planBuilder.addDependency(diamondPhase, parallelPhase);

        Plan plan = planBuilder.build();
        Assert.assertNotNull(plan);
    }

    @Test
    public void testCustomPlanFromServiceSpec() throws Block.InvalidException {
        DefaultBlockFactory blockFactory = new DefaultBlockFactory(stateStore);
        DefaultPhaseFactory phaseFactory = new DefaultPhaseFactory(blockFactory);
        Iterator<TaskSet> taskSetIterator = serviceSpecification.getTaskSets().iterator();

        Phase parallelPhase = phaseFactory.getPhase(
                taskSetIterator.next(),
                new ParallelStrategy());

        Phase serialPhase = phaseFactory.getPhase(
                taskSetIterator.next(),
                new SerialStrategy());

        TaskSet taskSet = taskSetIterator.next();
        DefaultPhaseBuilder phaseBuilder = new DefaultPhaseBuilder("diamond");
        List<Block> blocks = blockFactory.getBlocks(taskSet.getTaskSpecifications());

        phaseBuilder.addDependency(blocks.get(3), blocks.get(1));
        phaseBuilder.addDependency(blocks.get(3), blocks.get(2));
        phaseBuilder.addDependency(blocks.get(1), blocks.get(0));
        phaseBuilder.addDependency(blocks.get(2), blocks.get(0));
        Phase diamondPhase = phaseBuilder.build();

        Plan plan = new DefaultPlan(
                "plan",
                Arrays.asList(parallelPhase, serialPhase, diamondPhase),
                new SerialStrategy<>());
        Assert.assertNotNull(plan);
    }

    private Phase getParallelPhase() throws DependencyStrategyHelper.InvalidDependencyException {
        DefaultPhaseBuilder phaseBuilder = new DefaultPhaseBuilder("parallel");
        phaseBuilder.add(blocks);
        return phaseBuilder.build();
    }

    private Phase getDiamondPhase() {
        DefaultPhaseBuilder phaseBuilder = new DefaultPhaseBuilder("diamond");
        phaseBuilder.addDependency(block3, block1);
        phaseBuilder.addDependency(block3, block2);
        phaseBuilder.addDependency(block1, block0);
        phaseBuilder.addDependency(block2, block0);

        return phaseBuilder.build();
    }

    private Phase getSerialPhase() {
        DefaultPhaseBuilder phaseBuilder = new DefaultPhaseBuilder("serial");
        phaseBuilder.addDependency(block3, block2);
        phaseBuilder.addDependency(block2, block1);
        phaseBuilder.addDependency(block1, block0);

        return phaseBuilder.build();
    }
}
