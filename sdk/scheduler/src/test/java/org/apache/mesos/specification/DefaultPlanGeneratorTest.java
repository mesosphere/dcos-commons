package org.apache.mesos.specification;

import org.apache.curator.test.TestingServer;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.ConfigurationUpdater;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.scheduler.DefaultScheduler;
import org.apache.mesos.scheduler.plan.Plan;
import org.apache.mesos.specification.yaml.RawPlan;
import org.apache.mesos.specification.yaml.RawServiceSpecification;
import org.apache.mesos.specification.yaml.YAMLServiceSpecFactory;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.state.StateStoreCache;
import org.apache.mesos.testing.CuratorTestUtils;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Collections;

public class DefaultPlanGeneratorTest {
    @Rule
    public EnvironmentVariables environmentVariables;

    private static TestingServer testingServer;

    private StateStore stateStore;
    private ConfigStore<ServiceSpec> configStore;
    private OfferRequirementProvider offerRequirementProvider;

    @BeforeClass
    public static void beforeAll() throws Exception {
        testingServer = new TestingServer();
    }

    @Before
    public void beforeEach() throws Exception {
        MockitoAnnotations.initMocks(this);
        CuratorTestUtils.clear(testingServer);
        environmentVariables = new EnvironmentVariables();
        environmentVariables.set("EXECUTOR_URI", "");

        StateStoreCache.resetInstanceForTests();
    }

    @Test
    public void test() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("full-manual-plan.yml").getFile());
        RawServiceSpecification rawServiceSpecification = YAMLServiceSpecFactory.generateRawSpecFromYAML(file);
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(rawServiceSpecification);

        stateStore = DefaultScheduler.createStateStore(
                serviceSpec,
                testingServer.getConnectString());
        configStore = DefaultScheduler.createConfigStore(
                serviceSpec,
                testingServer.getConnectString(),
                Collections.emptyList());
        ConfigurationUpdater.UpdateResult updateResult = DefaultScheduler
                .updateConfig(serviceSpec, stateStore, configStore);
        offerRequirementProvider = DefaultScheduler.createOfferRequirementProvider(stateStore, updateResult.targetId);

        Assert.assertNotNull(serviceSpec);

        DefaultPlanGenerator generator = new DefaultPlanGenerator(configStore, stateStore, offerRequirementProvider);
        for (RawPlan rawPlan : rawServiceSpecification.getPlans().values()) {
            Plan plan = generator.generate(rawPlan, serviceSpec.getPods());
            Assert.assertNotNull(plan);
            Assert.assertEquals(2, plan.getChildren().size());
            Assert.assertEquals(1, plan.getChildren().get(0).getChildren().size());
            Assert.assertEquals(1, plan.getChildren().get(1).getChildren().size());
        }
    }
}
