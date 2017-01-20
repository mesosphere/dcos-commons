package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.*;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
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
    private static final double CPU = 1.0;
    private static final PlacementRule ALLOW_ALL = new PlacementRule() {
        @Override
        public EvaluationOutcome filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
            return EvaluationOutcome.pass(this, "pass for test");
        }
    };

    @ClassRule
    public static final EnvironmentVariables environmentVariables =
            OfferRequirementTestUtils.getOfferRequirementProviderEnvironment();

    private DefaultOfferRequirementProvider provider;

    @Mock private StateStore stateStore;
    private PodInstance podInstance;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);

        podInstance = getPodInstance("valid-minimal-health.yml");

        provider = new DefaultOfferRequirementProvider(stateStore, UUID.randomUUID());
    }

    private DefaultPodInstance getPodInstance(String serviceSpecFileName) throws Exception {
        DefaultServiceSpec serviceSpec = ServiceSpecTestUtils.getPodInstance(serviceSpecFileName);

        PodSpec podSpec = DefaultPodSpec.newBuilder(serviceSpec.getPods().get(0))
                .placementRule(ALLOW_ALL)
                .build();

        serviceSpec = DefaultServiceSpec.newBuilder(serviceSpec)
                .pods(Arrays.asList(podSpec))
                .build();

        return new DefaultPodInstance(serviceSpec.getPods().get(0), 0);
    }

    @Test
    public void testPlacementPassthru() throws InvalidRequirementException {
        List<String> tasksToLaunch = TaskUtils.getTaskNames(podInstance);
        OfferRequirement offerRequirement = provider.getNewOfferRequirement(podInstance, tasksToLaunch);
        Assert.assertNotNull(offerRequirement);
        Assert.assertTrue(offerRequirement.getPlacementRuleOptional().isPresent());
    }

    @Test
    public void testNewOfferRequirement() throws InvalidRequirementException {
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> taskSpec.getGoal().equals(GoalState.RUNNING))
                .map(taskSpec -> taskSpec.getName())
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
    public void testNewOfferRequirementDocker() throws Exception {
        PodInstance dockerPodInstance = getPodInstance("valid-docker.yml");

        OfferRequirement offerRequirement = provider.getNewOfferRequirement(
                dockerPodInstance, TaskUtils.getTaskNames(dockerPodInstance));

        Protos.ContainerInfo containerInfo =
                offerRequirement.getExecutorRequirementOptional().get().getExecutorInfo().getContainer();
        Assert.assertEquals(containerInfo.getType(), Protos.ContainerInfo.Type.MESOS);
        Assert.assertEquals(containerInfo.getMesos().getImage().getDocker().getName(), "group/image");
    }

    @Test
    public void testExistingOfferRequirement() throws InvalidRequirementException {
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> taskSpec.getGoal().equals(GoalState.RUNNING))
                .map(taskSpec -> taskSpec.getName())
                .collect(Collectors.toList());

        Protos.Resource cpu = ResourceTestUtils.getExpectedCpu(CPU);
        Protos.TaskInfo taskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(cpu));
        String taskName = TaskSpec.getInstanceName(podInstance, podInstance.getPod().getTasks().get(0));
        when(stateStore.fetchTask(taskName)).thenReturn(Optional.of(taskInfo));
        OfferRequirement offerRequirement =
                provider.getExistingOfferRequirement(podInstance, tasksToLaunch);
        Assert.assertNotNull(offerRequirement);
    }
}
