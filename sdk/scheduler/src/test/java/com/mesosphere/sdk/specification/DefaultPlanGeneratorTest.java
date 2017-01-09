package com.mesosphere.sdk.specification;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.specification.yaml.RawPlan;
import com.mesosphere.sdk.specification.yaml.RawServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreCache;
import com.mesosphere.sdk.testing.CuratorTestUtils;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import org.apache.curator.test.TestingServer;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.ArrayList;
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
    public void testFullManualPlan() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("full-manual-plan.yml").getFile());
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
            Assert.assertEquals(2, plan.getChildren().size());
            Assert.assertEquals(1, plan.getChildren().get(0).getChildren().size());
            Assert.assertEquals(1, plan.getChildren().get(1).getChildren().size());
        }
    }

    @Test
    public void testPartialManualPlan() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("partial-manual-plan.yml").getFile());
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
            Assert.assertEquals(2, plan.getChildren().size());
            Assert.assertEquals(2, plan.getChildren().get(0).getChildren().size());
            Assert.assertEquals(2, plan.getChildren().get(1).getChildren().size());

            PodInstanceRequirement podInstanceRequirement =
                    plan.getChildren().get(0).getChildren().get(0).start().get();
            List<String> tasksToLaunch = new ArrayList<>(podInstanceRequirement.getTasksToLaunch());
            Assert.assertEquals(1, tasksToLaunch.size());
            Assert.assertEquals("server", tasksToLaunch.get(0));

            podInstanceRequirement =
                    plan.getChildren().get(0).getChildren().get(1).start().get();
            tasksToLaunch = new ArrayList<>(podInstanceRequirement.getTasksToLaunch());
            Assert.assertEquals(1, tasksToLaunch.size());
            Assert.assertEquals("server", tasksToLaunch.get(0));

            podInstanceRequirement =
                    plan.getChildren().get(1).getChildren().get(0).start().get();
            tasksToLaunch = new ArrayList<>(podInstanceRequirement.getTasksToLaunch());
            Assert.assertEquals(1, tasksToLaunch.size());
            Assert.assertEquals("once", tasksToLaunch.get(0));

            podInstanceRequirement =
                    plan.getChildren().get(1).getChildren().get(1).start().get();
            tasksToLaunch = new ArrayList<>(podInstanceRequirement.getTasksToLaunch());
            Assert.assertEquals(1, tasksToLaunch.size());
            Assert.assertEquals("once", tasksToLaunch.get(0));
        }
    }
}
