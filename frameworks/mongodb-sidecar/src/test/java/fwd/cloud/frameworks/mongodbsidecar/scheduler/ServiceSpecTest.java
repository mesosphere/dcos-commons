package fwd.cloud.frameworks.mongodbsidecar.scheduler;

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
        environmentVariables.set("SERVICE_NAME", "mongodb-sidecar");
        environmentVariables.set("SERVICE_PRINCIPAL", "mongodb-sidecar-principal");
        environmentVariables.set("EXECUTOR_URI", "");
        environmentVariables.set("PORT_API", "8080");
        environmentVariables.set("MONGODB_PORT", "27017");
        environmentVariables.set("MONGODB_REST_PORT", "28017");
        environmentVariables.set("MONGODB_COUNT", "3");
        environmentVariables.set("MONGODB_CPUS", "0.1");
        environmentVariables.set("MONGODB_MEM", "1024");
        environmentVariables.set("MONGODB_DISK", "1000");
        environmentVariables.set("SIDECAR_COUNT", "1");
        environmentVariables.set("SIDECAR_CPUS", "0.2");
        environmentVariables.set("SIDECAR_MEM", "128");
        environmentVariables.set("SIDECAR_DISK", "0");
        environmentVariables.set("SIDECAR_APP_NAME", "blub");
        environmentVariables.set("SIDECAR_ADD_DELAY", "10");
        environmentVariables.set("SIDECAR_IMAGE", "alpine");
        environmentVariables.set("SIDECAR_CMD", "sleep 100");
        environmentVariables.set("SIDECAR_ZK_URL", "zk://zk-1.zk:2181");
        environmentVariables.set("SIDECAR_MESOS_URL", "http://master.mezos/mesos");


        // URL resource = ServiceSpecTest.class.getClassLoader().getResource("init.sh.mustache");
        // environmentVariables.set("CONFIG_TEMPLATE_PATH", new File(resource.getPath()).getParent());
        environmentVariables.set("LIBMESOS_URI", "");
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testYmlBase() throws Exception {
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
