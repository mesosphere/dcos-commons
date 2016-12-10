package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos.TaskInfo;
import com.mesosphere.sdk.config.DefaultTaskConfigRouter;
import com.mesosphere.sdk.offer.constrain.PassthroughRule;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.Mockito.when;

/**
 * This class tests the DefaultOfferRequirementProvider.
 */
public class DefaultOfferRequirementProviderTest {
    private static final TaskInfo TASK_INFO =
            TaskTestUtils.getTaskInfo(Arrays.asList(ResourceTestUtils.getExpectedCpu(1.0)));

    @ClassRule
    public static final EnvironmentVariables environmentVariables =
            OfferRequirementTestUtils.getOfferRequirementProviderEnvironment();

    private DefaultOfferRequirementProvider provider;

    @Mock private StateStore stateStore;

    @Mock private PodSpec podSpec;
    @Mock private PodInstance podInstance;
    @Mock private HealthCheckSpec healthCheckSpec;
    @Mock private CommandSpec commandSpec;
    @Mock private TaskSpec taskSpec;
    @Mock private ResourceSet resourceSet;

    private final ResourceSpecification resourceSpecification = new DefaultResourceSpecification(
            "cpus",
            ValueUtils.getValue(ResourceTestUtils.getDesiredCpu(1.0)),
            TestConstants.ROLE,
            TestConstants.PRINCIPAL,
            "CPUS");

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);


        when(podInstance.getPod()).thenReturn(podSpec);
        when(podInstance.getIndex()).thenReturn(0);

        when(healthCheckSpec.getCommand()).thenReturn(TestConstants.HEALTH_CHECK_CMD);
        when(healthCheckSpec.getMaxConsecutiveFailures()).thenReturn(3);
        when(healthCheckSpec.getDelay()).thenReturn(0);
        when(healthCheckSpec.getInterval()).thenReturn(0);
        when(healthCheckSpec.getTimeout()).thenReturn(0);
        when(healthCheckSpec.getGracePeriod()).thenReturn(0);

        when(commandSpec.getValue()).thenReturn(TestConstants.TASK_CMD);

        when(taskSpec.getName()).thenReturn(TestConstants.TASK_NAME);
        when(taskSpec.getType()).thenReturn(TestConstants.POD_TYPE);
        when(taskSpec.getResourceSet()).thenReturn(resourceSet);
        when(taskSpec.getCommand()).thenReturn(Optional.of(commandSpec));
        when(taskSpec.getHealthCheck()).thenReturn(Optional.of(healthCheckSpec));
        when(taskSpec.getGoal()).thenReturn(TaskSpec.GoalState.RUNNING);

        when(resourceSet.getResources()).thenReturn(Arrays.asList(resourceSpecification));
        when(resourceSet.getId()).thenReturn(TestConstants.RESOURCE_SET_ID);

        when(podSpec.getType()).thenReturn(TestConstants.POD_TYPE);
        when(podSpec.getUser()).thenReturn(Optional.empty());
        when(podSpec.getContainer()).thenReturn(Optional.empty());
        when(podSpec.getTasks()).thenReturn(Arrays.asList(taskSpec));
        when(podSpec.getResources()).thenReturn(Arrays.asList(resourceSet));
        when(podSpec.getAvoidTypes()).thenReturn(Collections.emptyList());
        when(podSpec.getColocateTypes()).thenReturn(Collections.emptyList());
        when(podSpec.getPlacementRule()).thenReturn(Optional.empty());

        provider = new DefaultOfferRequirementProvider(new DefaultTaskConfigRouter(), stateStore, UUID.randomUUID());
    }

    /*
    @Test
    public void testAddNewDesiredResource() throws InvalidRequirementException {
        Protos.Resource cpu = ResourceTestUtils.getExpectedCpu(CPU);
        Protos.Resource mem = ResourceTestUtils.getDesiredMem(MEM);
        Protos.TaskInfo taskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(cpu));

        // Add memory requirement to the new TaskSpecification
        TaskSpecification taskSpecification = setupMock(taskInfo.toBuilder().addResources(mem).build());

        OfferRequirement offerRequirement =
                PROVIDER.getExistingOfferRequirement(taskInfo, taskSpecification);
        Assert.assertNotNull(offerRequirement);
        Assert.assertFalse(offerRequirement.getPersistenceIds().contains(TestConstants.PERSISTENCE_ID));
        Assert.assertTrue(offerRequirement.getResourceIds().contains(TestConstants.RESOURCE_ID));
    }
    */

    @Test(expected=InvalidRequirementException.class)
    public void testNewOfferRequirementEmptyResourceSets() throws InvalidRequirementException {
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> taskSpec.getGoal().equals(TaskSpec.GoalState.RUNNING))
                .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
                .collect(Collectors.toList());

        when(resourceSet.getResources()).thenReturn(Collections.emptyList());

        provider.getNewOfferRequirement(podInstance, tasksToLaunch);
    }

    @Test(expected=InvalidRequirementException.class)
    public void testExistingOfferRequirementEmptyResourceSets() throws InvalidRequirementException {
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> taskSpec.getGoal().equals(TaskSpec.GoalState.RUNNING))
                .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
                .collect(Collectors.toList());

        when(resourceSet.getResources()).thenReturn(Collections.emptyList());

        String taskName = TaskSpec.getInstanceName(podInstance, podInstance.getPod().getTasks().get(0));
        when(stateStore.fetchTask(taskName)).thenReturn(Optional.of(TASK_INFO));
        provider.getExistingOfferRequirement(podInstance, tasksToLaunch);
    }

    @Test
    public void testNewOfferRequirement() throws InvalidRequirementException {
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> taskSpec.getGoal().equals(TaskSpec.GoalState.RUNNING))
                .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
                .collect(Collectors.toList());

        OfferRequirement offerRequirement = provider.getNewOfferRequirement(podInstance, tasksToLaunch);
        Assert.assertNotNull(offerRequirement);
        Assert.assertEquals(TestConstants.POD_TYPE, offerRequirement.getType());
        Assert.assertEquals(1, offerRequirement.getTaskRequirements().size());

        TaskRequirement taskRequirement = offerRequirement.getTaskRequirements().stream().findFirst().get();
        TaskInfo taskInfo = taskRequirement.getTaskInfo();
        Assert.assertEquals(TestConstants.TASK_CMD, taskInfo.getCommand().getValue());
        Assert.assertEquals(TestConstants.HEALTH_CHECK_CMD, taskInfo.getHealthCheck().getCommand().getValue());
        Assert.assertFalse(taskInfo.hasContainer());
    }

    @Test
    public void testNewOfferRequirementManyPlacementConstraints() throws InvalidRequirementException {
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> taskSpec.getGoal().equals(TaskSpec.GoalState.RUNNING))
                .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
                .collect(Collectors.toList());

        when(podSpec.getPlacementRule()).thenReturn(Optional.of(new PassthroughRule()));
        when(podSpec.getAvoidTypes()).thenReturn(Arrays.asList("avoid1", "avoid2"));
        when(podSpec.getColocateTypes()).thenReturn(Arrays.asList("colocate1"));

        OfferRequirement offerRequirement = provider.getNewOfferRequirement(podInstance, tasksToLaunch);
        Assert.assertEquals(
                "AndRule{rules=[" +
                "AndRule{rules=[" +
                "TaskTypeRule{type=avoid1, converter=TaskTypeLabelConverter{}, behavior=AVOID}, " +
                "TaskTypeRule{type=avoid2, converter=TaskTypeLabelConverter{}, behavior=AVOID}" +
                "]}, " +
                "TaskTypeRule{type=colocate1, converter=TaskTypeLabelConverter{}, behavior=COLOCATE}, " +
                "PassthroughRule{}" +
                "]}", offerRequirement.getPlacementRuleOptional().get().toString());
    }

    @Test
    public void testExistingOfferRequirement() throws InvalidRequirementException {
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> taskSpec.getGoal().equals(TaskSpec.GoalState.RUNNING))
                .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
                .collect(Collectors.toList());

        String taskName = TaskSpec.getInstanceName(podInstance, podInstance.getPod().getTasks().get(0));
        when(stateStore.fetchTask(taskName)).thenReturn(Optional.of(TASK_INFO));
        OfferRequirement offerRequirement =
                provider.getExistingOfferRequirement(podInstance, tasksToLaunch);
        Assert.assertNotNull(offerRequirement);
    }

    @Test
    public void testExistingOfferRequirementManyPlacementConstraints() throws InvalidRequirementException {
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> taskSpec.getGoal().equals(TaskSpec.GoalState.RUNNING))
                .map(taskSpec -> TaskSpec.getInstanceName(podInstance, taskSpec))
                .collect(Collectors.toList());

        when(podSpec.getPlacementRule()).thenReturn(Optional.of(new PassthroughRule()));
        when(podSpec.getAvoidTypes()).thenReturn(Arrays.asList("avoid1"));
        when(podSpec.getColocateTypes()).thenReturn(Arrays.asList("colocate1", "colocate2"));

        String taskName = TaskSpec.getInstanceName(podInstance, podInstance.getPod().getTasks().get(0));
        when(stateStore.fetchTask(taskName)).thenReturn(Optional.of(TASK_INFO));
        OfferRequirement offerRequirement =
                provider.getExistingOfferRequirement(podInstance, tasksToLaunch);
        Assert.assertEquals(
                "AndRule{rules=[" +
                "TaskTypeRule{type=avoid1, converter=TaskTypeLabelConverter{}, behavior=AVOID}, " +
                "OrRule{rules=[" +
                "TaskTypeRule{type=colocate1, converter=TaskTypeLabelConverter{}, behavior=COLOCATE}, " +
                "TaskTypeRule{type=colocate2, converter=TaskTypeLabelConverter{}, behavior=COLOCATE}" +
                "]}, " +
                "PassthroughRule{}" +
                "]}", offerRequirement.getPlacementRuleOptional().get().toString());
    }
}
