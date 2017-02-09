package com.mesosphere.sdk.specification;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.yaml.RawPlan;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreCache;
import com.mesosphere.sdk.testutils.CuratorTestUtils;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import org.apache.curator.test.TestingServer;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link DefaultPlanGenerator}.
 */
public class DefaultPlanGeneratorTest {

    @ClassRule
    public static final EnvironmentVariables environmentVariables =
            OfferRequirementTestUtils.getOfferRequirementProviderEnvironment();

    private static TestingServer testingServer;

    private StateStore stateStore;
    private ConfigStore<ServiceSpec> configStore;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testingServer = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        CuratorTestUtils.clear(testingServer);

        StateStoreCache.resetInstanceForTests();
    }

    @Test
    public void testCustomPhases() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("custom-phases.yml").getFile());
        RawServiceSpec rawServiceSpec = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpec);

        stateStore = DefaultScheduler.createStateStore(
                serviceSpec,
                testingServer.getConnectString());
        configStore = DefaultScheduler.createConfigStore(serviceSpec, testingServer.getConnectString());

        Assert.assertNotNull(serviceSpec);

        DefaultPlanGenerator generator = new DefaultPlanGenerator(configStore, stateStore);
        for (Map.Entry<String, RawPlan> entry : rawServiceSpec.getPlans().entrySet()) {
            Plan plan = generator.generate(entry.getValue(), entry.getKey(), serviceSpec.getPods());
            Assert.assertNotNull(plan);
            Assert.assertEquals(6, plan.getChildren().size());

            Phase serverPhase = plan.getChildren().get(0);
            Phase oncePhase = plan.getChildren().get(1);
            Phase interleavePhase = plan.getChildren().get(2);
            Phase fullCustomPhase = plan.getChildren().get(3);
            Phase partialCustomPhase = plan.getChildren().get(4);
            Phase omitStepPhase = plan.getChildren().get(5);

            validatePhase(
                    serverPhase,
                    Arrays.asList(
                            Arrays.asList("server"),
                            Arrays.asList("server"),
                            Arrays.asList("server")));

            validatePhase(
                    oncePhase,
                    Arrays.asList(
                            Arrays.asList("once"),
                            Arrays.asList("once"),
                            Arrays.asList("once")));

            validatePhase(
                    interleavePhase,
                    Arrays.asList(
                            Arrays.asList("once"),
                            Arrays.asList("server"),
                            Arrays.asList("once"),
                            Arrays.asList("server"),
                            Arrays.asList("once"),
                            Arrays.asList("server")));

            validatePhase(
                    fullCustomPhase,
                    Arrays.asList(
                            Arrays.asList("once"),
                            Arrays.asList("server"),
                            Arrays.asList("server"),
                            Arrays.asList("once"),
                            Arrays.asList("server")));

            validatePhase(
                    partialCustomPhase,
                    Arrays.asList(
                            Arrays.asList("server"),
                            Arrays.asList("once"),
                            Arrays.asList("once"),
                            Arrays.asList("server"),
                            Arrays.asList("server"),
                            Arrays.asList("once")));

            validatePhase(
                    omitStepPhase,
                    Arrays.asList(
                            Arrays.asList("once"),
                            Arrays.asList("server")));
            Assert.assertEquals("hello-1:[once]", omitStepPhase.getChildren().get(0).getName());
            Assert.assertEquals("hello-1:[server]", omitStepPhase.getChildren().get(1).getName());
        }
    }

    private void validatePhase(Phase phase, List<List<String>> stepTasks) {
        Assert.assertEquals(phase.getChildren().size(), stepTasks.size());
        for (int i = 0; i < stepTasks.size(); i++) {
            PodInstanceRequirement podInstanceRequirement = phase.getChildren().get(i).start().get();
            List<String> tasksToLaunch = new ArrayList<>(podInstanceRequirement.getTasksToLaunch());

            for (int j = 0; j < tasksToLaunch.size(); j++) {
                Assert.assertEquals(tasksToLaunch.get(j), stepTasks.get(i).get(j));
            }
        }
    }
}
