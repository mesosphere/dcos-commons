package com.mesosphere.sdk.kafka.scheduler;

import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.ConfigurationUpdater;
import org.apache.mesos.offer.OfferRequirementProvider;
import org.apache.mesos.scheduler.DefaultScheduler;
import org.apache.mesos.specification.DefaultServiceSpec;
import org.apache.mesos.specification.yaml.YAMLServiceSpecFactory;
import org.apache.mesos.state.StateStore;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.net.URL;
import java.util.Collections;

import static org.apache.mesos.specification.yaml.YAMLServiceSpecFactory.generateRawSpecFromYAML;

public class ServiceSpecTest {
    @ClassRule
    public static final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Mock
    private SchedulerDriver mockSchedulerDriver;

    @BeforeClass
    public static void beforeAll() {
        environmentVariables.set("EXECUTOR_URI", "");
        environmentVariables.set("PORT0", "8080");
        environmentVariables.set("BROKER_COUNT", "2");
        environmentVariables.set("BROKER_CPUS", "0.1");
        environmentVariables.set("BROKER_MEM", "512");
        environmentVariables.set("BROKER_DISK", "5000");
        URL resource = ServiceSpecTest.class.getClassLoader().getResource("server.properties.mustache");
        environmentVariables.set("CONFIG_TEMPLATE_PATH", new File(resource.getPath()).getParent());
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testServiceSpecDeserialization() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("svc.yml").getFile());

        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateServiceSpec(generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
        Assert.assertEquals(8080, serviceSpec.getApiPort());
        DefaultServiceSpec.getFactory(serviceSpec, Collections.emptyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testServiceSpecValidation() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("svc.yml").getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateServiceSpec(generateRawSpecFromYAML(file));

        TestingServer testingServer = new TestingServer();
        StateStore stateStore = DefaultScheduler.createStateStore(
                serviceSpec,
                testingServer.getConnectString());
        ConfigStore configStore = DefaultScheduler.createConfigStore(
                serviceSpec,
                testingServer.getConnectString(),
                Collections.emptyList());

        Protos.FrameworkID FRAMEWORK_ID =
                Protos.FrameworkID.newBuilder()
                        .setValue("test-framework-id")
                        .build();

        Protos.MasterInfo MASTER_INFO =
                Protos.MasterInfo.newBuilder()
                        .setId("test-master-id")
                        .setIp(0)
                        .setPort(0)
                        .build();

        ConfigurationUpdater.UpdateResult configUpdateResult = DefaultScheduler
                .updateConfig(serviceSpec, stateStore, configStore);

        OfferRequirementProvider offerRequirementProvider = DefaultScheduler
                .createOfferRequirementProvider(stateStore, configUpdateResult.targetId);

        DefaultScheduler defaultScheduler = DefaultScheduler
                .create(serviceSpec, stateStore, configStore, offerRequirementProvider);
        defaultScheduler.registered(mockSchedulerDriver, FRAMEWORK_ID, MASTER_INFO);
    }
}
