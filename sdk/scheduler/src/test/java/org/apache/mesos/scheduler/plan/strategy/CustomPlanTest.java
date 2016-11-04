package org.apache.mesos.scheduler.plan.strategy;

import org.apache.mesos.config.ConfigTargetStore;
import org.apache.mesos.offer.InvalidRequirementException;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.scheduler.plan.*;
import org.apache.mesos.specification.*;
import org.apache.mesos.state.StateStore;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.mockito.Mockito.*;

/**
 * These tests do not validate plan behavior.  See the PhaseBuilder, PlanBuilder, and Strategy tests.
 * These test serve as validation of ease of custom plan construction, similar to the CustomTaskSetTest.
 */
public class CustomPlanTest {
    @Mock
    Step step0;
    @Mock
    Step step1;
    @Mock
    Step step2;
    @Mock
    Step step3;

    private static final String SERVICE_NAME = "test-service";

    private static final int TASK_COUNT = 4;
    private static final String TASK_NAME = "A";
    private static final double TASK_CPU = 1.0;
    private static final double TASK_MEM = 1000.0;
    private static final double TASK_DISK = 1500.0;
    private static final String TASK_CMD = "echo " + TASK_NAME;

    private Collection<Step> steps;
    private ServiceSpecification serviceSpecification;

    @Mock private ConfigTargetStore mockConfigTargetStore;
    @Mock private StateStore mockStateStore;
    @Mock private OfferRequirementProvider mockOfferRequirementProvider;
    @Mock private OfferRequirement mockOfferRequirement;
    @Mock private TaskSpecificationProvider mockTaskSpecificationProvider;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);

        when(step0.getStrategy()).thenReturn(new SerialStrategy<>());
        when(step1.getStrategy()).thenReturn(new SerialStrategy<>());
        when(step2.getStrategy()).thenReturn(new SerialStrategy<>());
        when(step3.getStrategy()).thenReturn(new SerialStrategy<>());

        when(step0.getName()).thenReturn("step0");
        when(step1.getName()).thenReturn("step1");
        when(step2.getName()).thenReturn("step2");
        when(step3.getName()).thenReturn("step3");

        when(step0.getStatus()).thenReturn(Status.PENDING);
        when(step1.getStatus()).thenReturn(Status.PENDING);
        when(step2.getStatus()).thenReturn(Status.PENDING);
        when(step3.getStatus()).thenReturn(Status.PENDING);

        when(step0.isPending()).thenReturn(true);
        when(step1.isPending()).thenReturn(true);
        when(step2.isPending()).thenReturn(true);
        when(step3.isPending()).thenReturn(true);

        steps = Arrays.asList(step0, step1, step2, step3);

        serviceSpecification = new DefaultServiceSpecification(
                SERVICE_NAME,
                Arrays.asList(
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
                                TASK_DISK)));
    }

    @Test
    public void testCustomPlanFromPhasesDoesntThrow() throws DependencyStrategyHelper.InvalidDependencyException {
        Phase parallelPhase = getParallelPhase();
        Phase serialPhase = getSerialPhase();
        Phase diamondPhase = getDiamondPhase();

        DefaultPlanBuilder planBuilder = new DefaultPlanBuilder("custom");
        planBuilder.addDependency(serialPhase, diamondPhase);
        planBuilder.addDependency(diamondPhase, parallelPhase);

        planBuilder.build();
    }

    @Test
    public void testCustomPlanFromServiceSpecDoesntThrow()
            throws Step.InvalidStepException, InvalidRequirementException {
        StepFactory stepFactory = new DefaultStepFactory(
                mockConfigTargetStore, mockStateStore, mockOfferRequirementProvider);
        when(mockStateStore.fetchTask(anyString())).thenReturn(Optional.empty());
        when(mockOfferRequirementProvider.getNewOfferRequirement(any(TaskSpecification.class))).thenReturn(mockOfferRequirement);
        DefaultPhaseFactory phaseFactory = new DefaultPhaseFactory(stepFactory);
        Iterator<TaskSet> taskSetIterator = serviceSpecification.getTaskSets().iterator();

        Phase parallelPhase = phaseFactory.getPhase(
                taskSetIterator.next(),
                new ParallelStrategy<>());

        Phase serialPhase = phaseFactory.getPhase(
                taskSetIterator.next(),
                new SerialStrategy<>());

        TaskSet taskSet = taskSetIterator.next();
        DefaultPhaseBuilder phaseBuilder = new DefaultPhaseBuilder("diamond");
        List<Step> steps = getSteps(taskSet.getTaskSpecifications(), stepFactory);

        phaseBuilder.addDependency(steps.get(3), steps.get(1));
        phaseBuilder.addDependency(steps.get(3), steps.get(2));
        phaseBuilder.addDependency(steps.get(1), steps.get(0));
        phaseBuilder.addDependency(steps.get(2), steps.get(0));
        Phase diamondPhase = phaseBuilder.build();

        new DefaultPlan(
                "plan",
                Arrays.asList(parallelPhase, serialPhase, diamondPhase),
                new SerialStrategy<>());
    }

    private List<Step> getSteps(List<TaskSpecification> taskSpecifications, StepFactory stepFactory)
            throws Step.InvalidStepException {
        List<Step> steps = new ArrayList<>();
        for (TaskSpecification taskSpecification : taskSpecifications) {
            steps.add(stepFactory.getStep(taskSpecification));
        }

        return steps;
    }

    private Phase getParallelPhase() throws DependencyStrategyHelper.InvalidDependencyException {
        DefaultPhaseBuilder phaseBuilder = new DefaultPhaseBuilder("parallel");
        phaseBuilder.addAll(steps);
        return phaseBuilder.build();
    }

    private Phase getDiamondPhase() {
        DefaultPhaseBuilder phaseBuilder = new DefaultPhaseBuilder("diamond");
        phaseBuilder.addDependency(step3, step1);
        phaseBuilder.addDependency(step3, step2);
        phaseBuilder.addDependency(step1, step0);
        phaseBuilder.addDependency(step2, step0);

        return phaseBuilder.build();
    }

    private Phase getSerialPhase() {
        DefaultPhaseBuilder phaseBuilder = new DefaultPhaseBuilder("serial");
        phaseBuilder.addDependency(step3, step2);
        phaseBuilder.addDependency(step2, step1);
        phaseBuilder.addDependency(step1, step0);

        return phaseBuilder.build();
    }
}
