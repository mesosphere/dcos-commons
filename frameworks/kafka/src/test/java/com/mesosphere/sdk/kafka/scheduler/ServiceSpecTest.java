package com.mesosphere.sdk.kafka.scheduler;

import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.net.URL;
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
        ClassLoader classLoader = ServiceSpecTest.class.getClassLoader();
        File file = new File(classLoader.getResource("svc.yml").getFile());

        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateServiceSpec(generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
        Assert.assertEquals(8080, serviceSpec.getApiPort());
        DefaultServiceSpec.getFactory(serviceSpec, Collections.emptyList());
    }

    @Test
    public void testServiceSpecValidation() throws Exception {
        File file = new File(getClass().getClassLoader().getResource("svc.yml").getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(generateRawSpecFromYAML(file));

        TestingServer testingServer = new TestingServer();

        Protos.FrameworkID FRAMEWORK_ID = Protos.FrameworkID.newBuilder()
                .setValue("test-framework-id")
                .build();

        Protos.MasterInfo MASTER_INFO = Protos.MasterInfo.newBuilder()
                .setId("test-master-id")
                .setIp(0)
                .setPort(0)
                .build();

        Scheduler scheduler = DefaultScheduler.newBuilder(serviceSpec)
            .setStateStore(DefaultScheduler.createStateStore(serviceSpec, testingServer.getConnectString()))
            .setConfigStore(DefaultScheduler.createConfigStore(serviceSpec, testingServer.getConnectString()))
            .build();
        scheduler.registered(mockSchedulerDriver, FRAMEWORK_ID, MASTER_INFO);
        testingServer.close();
    }
}
