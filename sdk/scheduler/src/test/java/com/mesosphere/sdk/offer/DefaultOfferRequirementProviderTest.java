package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
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
import org.junit.Test;
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
    private static final SchedulerFlags flags = OfferRequirementTestUtils.getTestSchedulerFlags();
    private static final double CPU = 1.0;
    private static final PlacementRule ALLOW_ALL = new PlacementRule() {
        @Override
        public EvaluationOutcome filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
            return EvaluationOutcome.pass(this, "pass for test");
        }
    };

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
        provider = new DefaultOfferRequirementProvider(stateStore, TestConstants.SERVICE_NAME, uuid, flags);
    }

    private DefaultPodInstance getPodInstance(String serviceSpecFileName) throws Exception {
        DefaultServiceSpec serviceSpec =
                ServiceSpecTestUtils.getPodInstance(serviceSpecFileName, mockFileReader, flags);

        PodSpec podSpec = DefaultPodSpec.newBuilder(serviceSpec.getPods().get(0))
                .placementRule(ALLOW_ALL)
                .build();

        serviceSpec = DefaultServiceSpec.newBuilder(serviceSpec)
                .pods(Arrays.asList(podSpec))
                .build();

        return new DefaultPodInstance(serviceSpec.getPods().get(0), 0);
    }

    private List<String> getTasksToLaunch(PodInstance podInst) {
        return podInst.getPod().getTasks().stream()
                .filter(taskSpec -> taskSpec.getGoal().equals(GoalState.RUNNING))
                .map(TaskSpec::getName)
                .collect(Collectors.toList());
    }

    @Test
    public void testPlacementPassthru() throws InvalidRequirementException {
        List<String> tasksToLaunch = TaskUtils.getTaskNames(podInstance);
        OfferRequirement offerRequirement = provider.getNewOfferRequirement(
                PodInstanceRequirement.newBuilder(podInstance, tasksToLaunch).build());
        Assert.assertNotNull(offerRequirement);
        Assert.assertTrue(offerRequirement.getPlacementRuleOptional().isPresent());
    }

    private void finishNewOfferTest(OfferRequirement offerRequirement, List<String> tasksToLaunch,
                                    PodInstance podInstance) throws InvalidRequirementException {
        TaskRequirement taskRequirement = offerRequirement.getTaskRequirements().stream().findFirst().get();
        TaskInfo taskInfo = taskRequirement.getTaskInfo();
        Assert.assertEquals(TestConstants.HEALTH_CHECK_CMD, taskInfo.getHealthCheck().getCommand().getValue());

        Assert.assertTrue(taskInfo.hasContainer());
        Assert.assertTrue(taskInfo.hasCommand());

        Assert.assertEquals(taskInfo.getDiscovery().getVisibility(), Protos.DiscoveryInfo.Visibility.CLUSTER);
        Assert.assertEquals(taskInfo.getDiscovery().getName(), "meta-data-0");

        // Task command: what to run and envvars
        CommandInfo taskCommand = taskInfo.getCommand();
        Assert.assertEquals(TestConstants.TASK_CMD, taskCommand.getValue());

        Map<String, String> taskEnv = EnvUtils.fromEnvironmentToMap(taskCommand.getEnvironment());
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
        Assert.assertEquals("test-executor-uri", uris.get(2).getValue());
        Assert.assertEquals("test-libmesos-uri", uris.get(0).getValue());
        Assert.assertEquals("test-java-uri", uris.get(1).getValue());
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

    private void testOfferRequirementHasCorrectNetworkInfo(OfferRequirement offerRequirement,
                                                           boolean checkPortMapping, String expectedNetworkName) {
        if (offerRequirement.getExecutorRequirementOptional().isPresent()) {
            // Check for exactly 1 NetworkInfo
            Assert.assertEquals(1,
                    offerRequirement.getExecutorRequirementOptional().get()
                            .getExecutorInfo().getContainer().getNetworkInfosCount());
            Protos.NetworkInfo networkInfo = offerRequirement
                    .getExecutorRequirementOptional().get()
                    .getExecutorInfo().getContainer()
                    .getNetworkInfos(0);
            Assert.assertEquals(expectedNetworkName, networkInfo.getName());
            if (checkPortMapping) {
                Assert.assertTrue(DcosConstants.networkSupportsPortMapping(expectedNetworkName));
                Assert.assertEquals(TestConstants.NUMBER_OF_PORT_MAPPINGS, networkInfo.getPortMappingsCount());
                Assert.assertEquals(TestConstants.HOST_PORT, networkInfo.getPortMappings(0).getHostPort());
                Assert.assertEquals(TestConstants.CONTAINER_PORT, networkInfo
                        .getPortMappings(0).getContainerPort());
            } else {
                if (DcosConstants.networkSupportsPortMapping(expectedNetworkName)) {
                    // same as above (with mapping) except that the container port should me mapped to the same port on
                    // the host
                    Assert.assertEquals(networkInfo.getPortMappingsCount(), TestConstants.NUMBER_OF_PORT_MAPPINGS);
                    Assert.assertEquals(networkInfo.getPortMappings(0).getHostPort(), TestConstants.CONTAINER_PORT);
                    Assert.assertEquals(networkInfo.getPortMappings(0).getHostPort(),
                            networkInfo.getPortMappings(0).getContainerPort());
                } else {
                    // we don't map the ports if the overlay network doesn't explicitly allow it.
                    Assert.assertTrue(networkInfo.getPortMappingsCount() == 0);
                }
            }
       } else {
            Assert.fail();
        }
    }

    @Test
    public void testNewOfferRequirement() throws InvalidRequirementException {
        List<String> tasksToLaunch = getTasksToLaunch(podInstance);

        OfferRequirement offerRequirement = provider.getNewOfferRequirement(
                PodInstanceRequirement.newBuilder(podInstance, tasksToLaunch).build());
        Assert.assertNotNull(offerRequirement);
        Assert.assertEquals(TestConstants.POD_TYPE, offerRequirement.getType());
        Assert.assertEquals(1, offerRequirement.getTaskRequirements().size());
        finishNewOfferTest(offerRequirement, tasksToLaunch, podInstance);
    }

    @Test
    public void testNewOfferRequirementOnOverlayNetwork() throws Exception {
        PodInstance podInstance = getPodInstance("valid-minimal-overlay.yml");
        List<String> tasksToLaunch = getTasksToLaunch(podInstance);
        OfferRequirement offerRequirement = provider.getNewOfferRequirement(
                PodInstanceRequirement.newBuilder(podInstance, tasksToLaunch).build());
        Assert.assertNotNull(offerRequirement);  // check that everything loaded ok
        Assert.assertEquals(TestConstants.POD_TYPE, offerRequirement.getType());
        Assert.assertEquals(1, offerRequirement.getTaskRequirements().size());
        testOfferRequirementHasCorrectNetworkInfo(offerRequirement, false,
                DcosConstants.DEFAULT_OVERLAY_NETWORK);
        finishNewOfferTest(offerRequirement, tasksToLaunch, podInstance);
    }

    @Test
    public void testNewOfferRequirementOverlayNetworkWithPortForwarding() throws Exception {
        PodInstance networkPodInstance = getPodInstance("valid-networks-port-mapping.yml");
        List<String> tasksToLaunch = getTasksToLaunch(networkPodInstance);
        OfferRequirement offerRequirement = provider.getNewOfferRequirement(
                PodInstanceRequirement.newBuilder(networkPodInstance, tasksToLaunch).build());

        Assert.assertNotNull(offerRequirement);  // check that everything loaded ok
        Assert.assertEquals(TestConstants.POD_TYPE, offerRequirement.getType());
        Assert.assertEquals(1, offerRequirement.getTaskRequirements().size());
        testOfferRequirementHasCorrectNetworkInfo(offerRequirement, true, "mesos-bridge");
        finishNewOfferTest(offerRequirement, tasksToLaunch, networkPodInstance);
    }

    @Test
    public void testNewOfferRequiremenOverlayNetworkWithDocker() throws Exception {
        PodInstance dockerNetworkPodInstance = getPodInstance("valid-minimal-networks-docker.yml");
        List<String> tasksToLaunch = getTasksToLaunch(dockerNetworkPodInstance);
        OfferRequirement offerRequirement = provider.getNewOfferRequirement(
                PodInstanceRequirement.newBuilder(dockerNetworkPodInstance, tasksToLaunch).build());

        Assert.assertNotNull(offerRequirement);
        Assert.assertEquals(TestConstants.POD_TYPE, offerRequirement.getType());
        Assert.assertEquals(1, offerRequirement.getTaskRequirements().size());
        testOfferRequirementHasCorrectNetworkInfo(offerRequirement, false,
                DcosConstants.DEFAULT_OVERLAY_NETWORK);
        Protos.ContainerInfo containerInfo = offerRequirement
                .getExecutorRequirementOptional().get().getExecutorInfo().getContainer();
        Assert.assertEquals(containerInfo.getType(), Protos.ContainerInfo.Type.MESOS);
        Assert.assertEquals(containerInfo.getMesos().getImage().getDocker().getName(), "group/image");
        finishNewOfferTest(offerRequirement, tasksToLaunch, dockerNetworkPodInstance);
    }

    @Test
    public void testNewOfferRequirementDocker() throws Exception {
        PodInstance dockerPodInstance = getPodInstance("valid-image.yml");

        OfferRequirement offerRequirement = provider.getNewOfferRequirement(
                PodInstanceRequirement.newBuilder(
                        dockerPodInstance,
                        TaskUtils.getTaskNames(dockerPodInstance)).build());

        Assert.assertNotNull(offerRequirement);
        Assert.assertEquals("server", offerRequirement.getType());
        Assert.assertEquals(1, offerRequirement.getTaskRequirements().size());

        TaskRequirement taskRequirement = offerRequirement.getTaskRequirements().stream().findFirst().get();
        TaskInfo taskInfo = taskRequirement.getTaskInfo();

        Protos.ContainerInfo containerInfo =
                offerRequirement.getExecutorRequirementOptional().get().getExecutorInfo().getContainer();
        Assert.assertEquals(containerInfo.getType(), Protos.ContainerInfo.Type.MESOS);
        Assert.assertEquals(containerInfo.getMesos().getImage().getDocker().getName(), "group/image");

        Assert.assertTrue(taskInfo.hasContainer());
        Assert.assertTrue(taskInfo.hasCommand());

        Assert.assertEquals("cmd", taskInfo.getCommand().getValue());

        Assert.assertTrue(taskInfo.getCommand().getUrisList().isEmpty());

        Map<String, String> envvars = EnvUtils.fromEnvironmentToMap(taskInfo.getCommand().getEnvironment());
        Assert.assertEquals(envvars.toString(), 4, envvars.size());
        Assert.assertEquals(TestConstants.SERVICE_NAME, envvars.get("FRAMEWORK_NAME"));
        Assert.assertEquals(taskInfo.getName(), envvars.get("TASK_NAME"));
        Assert.assertEquals("true", envvars.get(taskInfo.getName()));
        Assert.assertEquals("0", envvars.get("POD_INSTANCE_INDEX"));
    }

    @Test
    public void testEnvironmentVariablesAddedToNewOfferRequirement() throws Exception {
        PodInstance dockerPodInstance = getPodInstance("valid-image.yml");
        Map<String, String> parameters = new HashMap<>();
        parameters.put("PARAM0", "value0");

        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(
                        dockerPodInstance,
                        TaskUtils.getTaskNames(dockerPodInstance)).build();
        OfferRequirement offerRequirement = provider.getNewOfferRequirement(podInstanceRequirement);

        TaskRequirement taskRequirement = offerRequirement.getTaskRequirements().stream().findFirst().get();
        TaskInfo taskInfo = taskRequirement.getTaskInfo();

        Map<String, String> envvars = EnvUtils.fromEnvironmentToMap(taskInfo.getCommand().getEnvironment());
        Assert.assertEquals(envvars.toString(), 4, envvars.size());
        Assert.assertEquals(null, envvars.get("PARAM0"));

        offerRequirement = provider.getNewOfferRequirement(
                PodInstanceRequirement.newBuilder(podInstanceRequirement)
                        .environment(parameters)
                        .build());

        taskRequirement = offerRequirement.getTaskRequirements().stream().findFirst().get();
        taskInfo = taskRequirement.getTaskInfo();

        envvars = EnvUtils.fromEnvironmentToMap(taskInfo.getCommand().getEnvironment());
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
                provider.getExistingOfferRequirement(
                        PodInstanceRequirement.newBuilder(podInstance, tasksToLaunch).build());
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
        PodInstanceRequirement podInstanceRequirement =
                PodInstanceRequirement.newBuilder(podInstance, tasksToLaunch).build();
        OfferRequirement offerRequirement = provider.getExistingOfferRequirement(podInstanceRequirement);

        TaskRequirement taskRequirement = offerRequirement.getTaskRequirements().stream().findFirst().get();
        taskInfo = taskRequirement.getTaskInfo();

        Map<String, String> envvars = EnvUtils.fromEnvironmentToMap(taskInfo.getCommand().getEnvironment());
        Assert.assertEquals(null, envvars.get("PARAM0"));

        offerRequirement = provider.getExistingOfferRequirement(
                PodInstanceRequirement.newBuilder(podInstanceRequirement)
                        .environment(parameters)
                        .build());

        taskRequirement = offerRequirement.getTaskRequirements().stream().findFirst().get();
        taskInfo = taskRequirement.getTaskInfo();

        envvars = EnvUtils.fromEnvironmentToMap(taskInfo.getCommand().getEnvironment());
        Assert.assertEquals("value0", envvars.get("PARAM0"));
    }
}
