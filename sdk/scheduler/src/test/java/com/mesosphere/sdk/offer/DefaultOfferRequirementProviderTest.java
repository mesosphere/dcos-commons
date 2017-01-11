package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.constrain.PlacementRule;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TaskTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
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

import java.io.File;
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
        public Offer filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
            return offer;
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
        environmentVariables.set("EXECUTOR_URI", "");
        environmentVariables.set("LIBMESOS_URI", "");
        environmentVariables.set("PORT0", "8080");

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("valid-minimal-health.yml").getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateServiceSpec(YAMLServiceSpecFactory.generateRawSpecFromYAML(file));

        PodSpec podSpec = DefaultPodSpec.newBuilder(serviceSpec.getPods().get(0))
                .placementRule(ALLOW_ALL)
                .build();

        serviceSpec = DefaultServiceSpec.newBuilder(serviceSpec)
                .pods(Arrays.asList(podSpec))
                .build();

        podInstance = new DefaultPodInstance(serviceSpec.getPods().get(0), 0);

        provider = new DefaultOfferRequirementProvider(stateStore, UUID.randomUUID());
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
