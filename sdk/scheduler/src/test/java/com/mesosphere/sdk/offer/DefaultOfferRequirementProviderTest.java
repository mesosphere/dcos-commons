package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.*;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.CommandInfo;
import org.apache.mesos.Protos.CommandInfo.URI;
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

import static com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory.*;
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
    @Mock private FileReader mockFileReader;
    private UUID uuid;
    private PodInstance podInstance;

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mockFileReader.read("config-one.conf.mustache")).thenReturn("hello");
        when(mockFileReader.read("config-two.xml.mustache")).thenReturn("hey");
        podInstance = getPodInstance("valid-minimal-health-configfile.yml");

        uuid = UUID.randomUUID();
        provider = new DefaultOfferRequirementProvider(stateStore, TestConstants.SERVICE_NAME, uuid);
    }

    private DefaultPodInstance getPodInstance(String serviceSpecFileName) throws Exception {
        DefaultServiceSpec serviceSpec = ServiceSpecTestUtils.getPodInstance(serviceSpecFileName, mockFileReader);

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
        OfferRequirement offerRequirement = provider.getNewOfferRequirement(
                PodInstanceRequirement.create(podInstance, tasksToLaunch));
        Assert.assertNotNull(offerRequirement);
        Assert.assertTrue(offerRequirement.getPlacementRuleOptional().isPresent());
    }

    @Test
    public void testNewOfferRequirement() throws InvalidRequirementException {
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> taskSpec.getGoal().equals(GoalState.RUNNING))
                .map(taskSpec -> taskSpec.getName())
                .collect(Collectors.toList());

        OfferRequirement offerRequirement = provider.getNewOfferRequirement(
                PodInstanceRequirement.create(podInstance, tasksToLaunch));
        Assert.assertNotNull(offerRequirement);
        Assert.assertEquals(TestConstants.POD_TYPE, offerRequirement.getType());
        Assert.assertEquals(1, offerRequirement.getTaskRequirements().size());

        TaskRequirement taskRequirement = offerRequirement.getTaskRequirements().stream().findFirst().get();
        TaskInfo taskInfo = taskRequirement.getTaskInfo();
        Assert.assertEquals(TestConstants.HEALTH_CHECK_CMD, taskInfo.getHealthCheck().getCommand().getValue());

        Assert.assertFalse(taskInfo.hasContainer());
        Assert.assertTrue(taskInfo.hasCommand());

        // Task command: what to run and envvars
        CommandInfo taskCommand = taskInfo.getCommand();
        Assert.assertEquals(TestConstants.TASK_CMD, taskCommand.getValue());

        Map<String, String> taskEnv = CommonTaskUtils.fromEnvironmentToMap(taskCommand.getEnvironment());
        Assert.assertEquals(taskEnv.toString(), 6, taskEnv.size());
        Assert.assertEquals(TestConstants.SERVICE_NAME, taskEnv.get("FRAMEWORK_NAME"));
        Assert.assertEquals(taskInfo.getName(), taskEnv.get("TASK_NAME"));
        Assert.assertEquals("true", taskEnv.get(taskInfo.getName()));
        Assert.assertEquals("0", taskEnv.get("POD_INSTANCE_INDEX"));
        Assert.assertEquals("config-templates/config-one,conf/config-one.conf",
                taskEnv.get("CONFIG_TEMPLATE_CONFIG_ONE"));
        Assert.assertEquals("config-templates/config-two,../other/conf/config-two.xml",
                taskEnv.get("CONFIG_TEMPLATE_CONFIG_TWO"));

        // Executor command: uris
        CommandInfo executorCommand =
                offerRequirement.getExecutorRequirementOptional().get().getExecutorInfo().getCommand();
        List<URI> uris = executorCommand.getUrisList();
        Assert.assertEquals(5, uris.size());
        Assert.assertEquals("test-executor-uri", uris.get(0).getValue());
        Assert.assertEquals("test-libmesos-uri", uris.get(1).getValue());
        Assert.assertEquals("https://downloads.mesosphere.com/java/jre-8u112-linux-x64-jce-unlimited.tar.gz", uris.get(2).getValue());
        String artifactDirUrl = String.format("http://api.%s.marathon.%s/v1/artifacts/template/%s/%s/%s/",
                TestConstants.SERVICE_NAME,
                ResourceUtils.VIP_HOST_TLD,
                uuid.toString(),
                podInstance.getPod().getType(),
                tasksToLaunch.get(0));
        Assert.assertEquals(artifactDirUrl + "config-one", uris.get(3).getValue());
        Assert.assertEquals("config-templates/config-one", uris.get(3).getOutputFile());
        Assert.assertEquals(artifactDirUrl + "config-two", uris.get(4).getValue());
        Assert.assertEquals("config-templates/config-two", uris.get(4).getOutputFile());
    }

    @Test
    public void testNewOfferRequirementDocker() throws Exception {
        PodInstance dockerPodInstance = getPodInstance("valid-docker.yml");

        OfferRequirement offerRequirement = provider.getNewOfferRequirement(
                PodInstanceRequirement.create(dockerPodInstance, TaskUtils.getTaskNames(dockerPodInstance)));

        Assert.assertNotNull(offerRequirement);
        Assert.assertEquals("server", offerRequirement.getType());
        Assert.assertEquals(1, offerRequirement.getTaskRequirements().size());

        TaskRequirement taskRequirement = offerRequirement.getTaskRequirements().stream().findFirst().get();
        TaskInfo taskInfo = taskRequirement.getTaskInfo();

        Protos.ContainerInfo containerInfo =
                offerRequirement.getExecutorRequirementOptional().get().getExecutorInfo().getContainer();
        Assert.assertEquals(containerInfo.getType(), Protos.ContainerInfo.Type.MESOS);
        Assert.assertEquals(containerInfo.getMesos().getImage().getDocker().getName(), "group/image");

        Assert.assertFalse(taskInfo.hasContainer());
        Assert.assertTrue(taskInfo.hasCommand());

        Assert.assertEquals("cmd", taskInfo.getCommand().getValue());

        Assert.assertTrue(taskInfo.getCommand().getUrisList().isEmpty());

        Map<String, String> envvars = CommonTaskUtils.fromEnvironmentToMap(taskInfo.getCommand().getEnvironment());
        Assert.assertEquals(envvars.toString(), 4, envvars.size());
        Assert.assertEquals(TestConstants.SERVICE_NAME, envvars.get("FRAMEWORK_NAME"));
        Assert.assertEquals(taskInfo.getName(), envvars.get("TASK_NAME"));
        Assert.assertEquals("true", envvars.get(taskInfo.getName()));
        Assert.assertEquals("0", envvars.get("POD_INSTANCE_INDEX"));
    }

    @Test
    public void testEnvironmentVariablesAddedToNewOfferRequirement() throws Exception {
        PodInstance dockerPodInstance = getPodInstance("valid-docker.yml");
        Map<String, String> parameters = new HashMap<>();
        parameters.put("PARAM0", "value0");

        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirement.create(
                    dockerPodInstance, TaskUtils.getTaskNames(dockerPodInstance));
        OfferRequirement offerRequirement = provider.getNewOfferRequirement(podInstanceRequirement);

        TaskRequirement taskRequirement = offerRequirement.getTaskRequirements().stream().findFirst().get();
        TaskInfo taskInfo = taskRequirement.getTaskInfo();

        Map<String, String> envvars = CommonTaskUtils.fromEnvironmentToMap(taskInfo.getCommand().getEnvironment());
        Assert.assertEquals(envvars.toString(), 4, envvars.size());
        Assert.assertEquals(null, envvars.get("PARAM0"));

        offerRequirement = provider.getNewOfferRequirement(podInstanceRequirement.withParameters(parameters));

        taskRequirement = offerRequirement.getTaskRequirements().stream().findFirst().get();
        taskInfo = taskRequirement.getTaskInfo();

        envvars = CommonTaskUtils.fromEnvironmentToMap(taskInfo.getCommand().getEnvironment());
        Assert.assertEquals(envvars.toString(), 5, envvars.size());
        Assert.assertEquals("value0", envvars.get("PARAM0"));
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
                provider.getExistingOfferRequirement(PodInstanceRequirement.create(podInstance, tasksToLaunch));
        Assert.assertNotNull(offerRequirement);
    }

    @Test
    public void testEnvironmentVariablesAddedToExistingOfferRequirement() throws Exception {
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .filter(taskSpec -> taskSpec.getGoal().equals(GoalState.RUNNING))
                .map(taskSpec -> taskSpec.getName())
                .collect(Collectors.toList());

        Protos.Resource cpu = ResourceTestUtils.getExpectedCpu(CPU);
        Protos.TaskInfo taskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(cpu));
        String taskName = TaskSpec.getInstanceName(podInstance, podInstance.getPod().getTasks().get(0));
        when(stateStore.fetchTask(taskName)).thenReturn(Optional.of(taskInfo));

        Map<String, String> parameters = new HashMap<>();
        parameters.put("PARAM0", "value0");
        PodInstanceRequirement podInstanceRequirement = PodInstanceRequirement.create(podInstance, tasksToLaunch);
        OfferRequirement offerRequirement = provider.getExistingOfferRequirement(podInstanceRequirement);

        TaskRequirement taskRequirement = offerRequirement.getTaskRequirements().stream().findFirst().get();
        taskInfo = taskRequirement.getTaskInfo();

        Map<String, String> envvars = CommonTaskUtils.fromEnvironmentToMap(taskInfo.getCommand().getEnvironment());
        Assert.assertEquals(null, envvars.get("PARAM0"));

        offerRequirement = provider.getExistingOfferRequirement(podInstanceRequirement.withParameters(parameters));

        taskRequirement = offerRequirement.getTaskRequirements().stream().findFirst().get();
        taskInfo = taskRequirement.getTaskInfo();

        envvars = CommonTaskUtils.fromEnvironmentToMap(taskInfo.getCommand().getEnvironment());
        Assert.assertEquals("value0", envvars.get("PARAM0"));
    }
}
