package com.mesosphere.sdk.scheduler.plan;

import org.apache.curator.test.TestingServer;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.curator.CuratorStateStore;
import com.mesosphere.sdk.offer.InvalidRequirementException;
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

    private static final TaskSpec taskSpec0 =
            TestPodFactory.getTaskSpec(TestConstants.TASK_NAME + 0, TestConstants.RESOURCE_SET_ID);
    private static final TaskSpec taskSpec1 =
            TestPodFactory.getTaskSpec(TestConstants.TASK_NAME + 1, TestConstants.RESOURCE_SET_ID);
    private static final PodSpec POD_SPEC = DefaultPodSpec.newBuilder()
            .type(TestConstants.POD_TYPE)
            .count(1)
            .tasks(Arrays.asList(taskSpec0, taskSpec1))
            .build();

    private static final PodInstance POD_INSTANCE = new DefaultPodInstance(POD_SPEC, 0);
    private static TestingServer testingServer;

    private StepFactory stepFactory;
    private ConfigStore<ServiceSpec> configStore;
    private StateStore stateStore;

    private static final ServiceSpec serviceSpec =
            DefaultServiceSpec.newBuilder()
                    .name(TestConstants.SERVICE_NAME)
                    .role(TestConstants.ROLE)
                    .principal(TestConstants.PRINCIPAL)
                    .apiPort(0)
                    .zookeeperConnection("foo.bar.com")
                    .pods(Arrays.asList(POD_SPEC))
                    .build();

    @BeforeClass
    public static void beforeAll() throws Exception {
        testingServer = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        CuratorTestUtils.clear(testingServer);

        stateStore = new CuratorStateStore(
                "test-framework-name",
                testingServer.getConnectString());

        configStore = DefaultScheduler.createConfigStore(serviceSpec, testingServer.getConnectString());

        UUID configId = configStore.store(serviceSpec);
        configStore.setTargetConfig(configId);

        stepFactory = new DefaultStepFactory(configStore, stateStore);
    }

    @Test(expected = Step.InvalidStepException.class)
    public void testGetStepFailsOnMultipleResourceSetReferences()
            throws InvalidRequirementException, Step.InvalidStepException {

        List<String> tasksToLaunch = POD_INSTANCE.getPod().getTasks().stream()
                .map(taskSpec -> taskSpec.getName())
                .collect(Collectors.toList());
        stepFactory.getStep(POD_INSTANCE, tasksToLaunch);
    }
}
