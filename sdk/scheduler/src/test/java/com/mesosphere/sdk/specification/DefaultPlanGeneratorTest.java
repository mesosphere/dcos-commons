package com.mesosphere.sdk.specification;

import org.apache.curator.test.TestingServer;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.specification.yaml.RawPlan;
import com.mesosphere.sdk.specification.yaml.RawServiceSpecification;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreCache;
import com.mesosphere.sdk.testing.CuratorTestUtils;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;

import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Collections;

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

        Assert.assertNotNull(serviceSpec);

        DefaultPlanGenerator generator = new DefaultPlanGenerator(configStore, stateStore);
        for (RawPlan rawPlan : rawServiceSpecification.getPlans().values()) {
            Plan plan = generator.generate(rawPlan, serviceSpec.getPods());
            Assert.assertNotNull(plan);
            Assert.assertEquals(2, plan.getChildren().size());
            Assert.assertEquals(1, plan.getChildren().get(0).getChildren().size());
            Assert.assertEquals(1, plan.getChildren().get(1).getChildren().size());
        }
    }
}
