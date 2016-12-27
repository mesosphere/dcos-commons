package com.mesosphere.sdk.helloworld.scheduler;

import org.apache.curator.test.TestingServer;
import org.apache.mesos.SchedulerDriver;
import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.config.ConfigurationUpdater;
import com.mesosphere.sdk.offer.OfferRequirementProvider;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreCache;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Collections;

import static com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory.generateRawSpecFromYAML;

public class ServiceSpecTest {

    @ClassRule
    public static final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Mock
    private SchedulerDriver mockSchedulerDriver;

    @BeforeClass
    public static void beforeAll() {
        environmentVariables.set("EXECUTOR_URI", "");
        environmentVariables.set("LIBMESOS_URI", "");
        environmentVariables.set("PORT0", "8080");

        environmentVariables.set("SLEEP_DURATION", "1000");
        environmentVariables.set("HELLO_COUNT", "2");
        environmentVariables.set("HELLO_PORT", "4444");
        environmentVariables.set("HELLO_VIP_NAME", "helloworld");
        environmentVariables.set("HELLO_VIP_PORT", "9999");
        environmentVariables.set("HELLO_CPUS", "0.1");
        environmentVariables.set("HELLO_MEM", "512");
        environmentVariables.set("HELLO_DISK", "5000");

        environmentVariables.set("WORLD_COUNT", "3");
        environmentVariables.set("WORLD_CPUS", "0.2");
        environmentVariables.set("WORLD_MEM", "1024");
        environmentVariables.set("WORLD_FAILS", "3");
        environmentVariables.set("WORLD_DISK", "5000");
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }


    @Test
    public void test_yml_base() throws Exception{
        ServiceSpecDeserialization("svc.yml");
    }

    @Test
    public void test_yml_simple() throws Exception{
        ServiceSpecDeserialization("svc_simple.yml");
    }

    @Test
    public void test_yml_withPlan() throws Exception{
        ServiceSpecDeserialization("svc_plan.yml");
    }

    @Test
    public void test_yml_withPlan_uris() throws Exception{
        ServiceSpecDeserialization("svc_uri.yml");
    }
    @Test
    public void test_validate_yml_base() throws Exception{
        ServiceSpecValidation("svc.yml");
    }

    @Test
    public void test_validate_yml_simple() throws Exception{
        ServiceSpecValidation("svc_simple.yml");
    }

    @Test
    public void test_validate_yml_withPlan() throws Exception{
        ServiceSpecValidation("svc_plan.yml");
    }

    @Test
    public void test_validate_yml_withPlan_uri() throws Exception{
        ServiceSpecValidation("svc_uri.yml");
    }

    private void ServiceSpecDeserialization(String fileName) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(fileName).getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateServiceSpec(generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
        Assert.assertEquals(8080, serviceSpec.getApiPort());
        DefaultServiceSpec.getFactory(serviceSpec, Collections.emptyList());
    }

    private void ServiceSpecValidation(String fileName) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(fileName).getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateServiceSpec(generateRawSpecFromYAML(file));

        TestingServer testingServer = new TestingServer();
        StateStoreCache.resetInstanceForTests();
        StateStore stateStore = DefaultScheduler.createStateStore(
                serviceSpec,
                testingServer.getConnectString());
        ConfigStore<ServiceSpec> configStore = DefaultScheduler.createConfigStore(
                serviceSpec,
                testingServer.getConnectString(),
                Collections.emptyList());

        ConfigurationUpdater.UpdateResult configUpdateResult =
                DefaultScheduler.updateConfig(serviceSpec, stateStore, configStore);

        OfferRequirementProvider offerRequirementProvider =
                DefaultScheduler.createOfferRequirementProvider(stateStore, configUpdateResult.targetId);

        DefaultScheduler.create(serviceSpec, stateStore, configStore, offerRequirementProvider);
    }
}
