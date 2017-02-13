package com.mesosphere.sdk.scheduler.plan;

import org.apache.curator.test.TestingServer;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.curator.CuratorStateStore;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.testutils.CuratorTestUtils;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * This class tests the {@link DefaultStepFactory} class.
 */
public class DefaultStepFactoryTest {

    @ClassRule
    public static final EnvironmentVariables environmentVariables =
            OfferRequirementTestUtils.getOfferRequirementProviderEnvironment();

    private static TestingServer testingServer;

    private StepFactory stepFactory;
    private ConfigStore<ServiceSpec> configStore;
    private StateStore stateStore;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testingServer = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
    }

    @Test(expected = Step.InvalidStepException.class)
    public void testGetStepFailsOnMultipleResourceSetReferences() throws Exception {

        PodInstance podInstance = getPodInstanceWithSameResourceSets();
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .map(taskSpec -> taskSpec.getName())
                .collect(Collectors.toList());
        stepFactory.getStep(podInstance, tasksToLaunch);
    }

    @Test(expected = Step.InvalidStepException.class)
    public void testGetStepFailsOnDuplicateDNSNames() throws Exception {

        PodInstance podInstance = getPodInstanceWithSameDnsPrefixes();
        List<String> tasksToLaunch = podInstance.getPod().getTasks().stream()
                .map(taskSpec -> taskSpec.getName())
                .collect(Collectors.toList());
        stepFactory.getStep(podInstance, tasksToLaunch);
    }

    private PodInstance getPodInstanceWithSameResourceSets() throws Exception {
        TaskSpec taskSpec0 =
                TestPodFactory.getTaskSpec(TestConstants.TASK_NAME + 0, TestConstants.RESOURCE_SET_ID);
        TaskSpec taskSpec1 =
                TestPodFactory.getTaskSpec(TestConstants.TASK_NAME + 1, TestConstants.RESOURCE_SET_ID);
        PodSpec podSpec = DefaultPodSpec.newBuilder()
                .type(TestConstants.POD_TYPE)
                .count(1)
                .tasks(Arrays.asList(taskSpec0, taskSpec1))
                .build();

        ServiceSpec serviceSpec =
                DefaultServiceSpec.newBuilder()
                        .name(TestConstants.SERVICE_NAME)
                        .role(TestConstants.ROLE)
                        .principal(TestConstants.PRINCIPAL)
                        .apiPort(0)
                        .zookeeperConnection("foo.bar.com")
                        .pods(Arrays.asList(podSpec))
                        .build();

        CuratorTestUtils.clear(testingServer);

        stateStore = new CuratorStateStore(
                "test-framework-name",
                testingServer.getConnectString());

        configStore = DefaultScheduler.createConfigStore(serviceSpec, testingServer.getConnectString());

        UUID configId = configStore.store(serviceSpec);
        configStore.setTargetConfig(configId);

        stepFactory = new DefaultStepFactory(configStore, stateStore);

        return new DefaultPodInstance(podSpec, 0);
    }

    private PodInstance getPodInstanceWithSameDnsPrefixes() throws Exception {
        TaskSpec taskSpec0 =
                TestPodFactory.getTaskSpec(
                        TestConstants.TASK_NAME + 0, TestConstants.RESOURCE_SET_ID + 0, TestConstants.TASK_DNS_PREFIX);
        TaskSpec taskSpec1 =
                TestPodFactory.getTaskSpec(
                        TestConstants.TASK_NAME + 1, TestConstants.RESOURCE_SET_ID + 1, TestConstants.TASK_DNS_PREFIX);
        PodSpec podSpec = DefaultPodSpec.newBuilder()
                .type(TestConstants.POD_TYPE)
                .count(1)
                .tasks(Arrays.asList(taskSpec0, taskSpec1))
                .build();

        ServiceSpec serviceSpec =
                DefaultServiceSpec.newBuilder()
                        .name(TestConstants.SERVICE_NAME)
                        .role(TestConstants.ROLE)
                        .principal(TestConstants.PRINCIPAL)
                        .apiPort(0)
                        .zookeeperConnection("foo.bar.com")
                        .pods(Arrays.asList(podSpec))
                        .build();

        CuratorTestUtils.clear(testingServer);

        stateStore = new CuratorStateStore(
                "test-framework-name",
                testingServer.getConnectString());

        configStore = DefaultScheduler.createConfigStore(serviceSpec, testingServer.getConnectString());

        UUID configId = configStore.store(serviceSpec);
        configStore.setTargetConfig(configId);

        stepFactory = new DefaultStepFactory(configStore, stateStore);

        return new DefaultPodInstance(podSpec, 0);
    }
}
