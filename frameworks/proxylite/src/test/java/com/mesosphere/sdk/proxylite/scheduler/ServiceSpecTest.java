package com.mesosphere.sdk.proxylite.scheduler;

import com.mesosphere.sdk.dcos.Capabilities;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.SchedulerDriver;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.StateStoreCache;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.Collections;

import static com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory.generateRawSpecFromYAML;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        environmentVariables.set("PROXY_LITE_CPUS", "0.1");
        environmentVariables.set("PROXY_LITE_MEM", "512");
        environmentVariables.set("BACKENDS", "backends");
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void test_yml_base() throws Exception {
        deserializeServiceSpec("svc.yml");
        validateServiceSpec("svc.yml");
    }

    private void deserializeServiceSpec(String fileName) throws Exception {
        File file = new File(getClass().getClassLoader().getResource(fileName).getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateServiceSpec(generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
        Assert.assertEquals(8080, serviceSpec.getApiPort());
        DefaultServiceSpec.getFactory(serviceSpec, Collections.emptyList());
    }

    private void validateServiceSpec(String fileName) throws Exception {
        File file = new File(getClass().getClassLoader().getResource(fileName).getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory.generateServiceSpec(generateRawSpecFromYAML(file));

        TestingServer testingServer = new TestingServer();
        StateStoreCache.resetInstanceForTests();

        Capabilities capabilities = mock(Capabilities.class);
        when(capabilities.supportsNamedVips()).thenReturn(true);
        when(capabilities.supportsRLimits()).thenReturn(true);

        DefaultScheduler.newBuilder(serviceSpec)
                .setStateStore(DefaultScheduler.createStateStore(serviceSpec, testingServer.getConnectString()))
                .setConfigStore(DefaultScheduler.createConfigStore(serviceSpec, testingServer.getConnectString()))
                .setCapabilities(capabilities)
                .build();
        testingServer.close();
    }
}
