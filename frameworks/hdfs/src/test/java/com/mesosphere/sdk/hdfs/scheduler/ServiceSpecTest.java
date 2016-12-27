package com.mesosphere.sdk.hdfs.scheduler;

import com.mesosphere.sdk.config.ConfigStore;
import com.mesosphere.sdk.config.ConfigurationUpdater;
import com.mesosphere.sdk.offer.OfferRequirementProvider;
import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreCache;
import org.apache.curator.test.TestingServer;
import org.apache.mesos.SchedulerDriver;
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
        environmentVariables.set("SERVICE_NAME", "hdfs");
        environmentVariables.set("SERVICE_PRINCIPAL", "principal");
        environmentVariables.set("JOURNAL_CPUS", "1.0");
        environmentVariables.set("JOURNAL_MEM", "1024");
        environmentVariables.set("JOURNAL_DISK", "1024");
        environmentVariables.set("JOURNAL_DISK_TYPE", "MOUNT");
        environmentVariables.set("JOURNAL_NODE_RPC_PORT", "1");
        environmentVariables.set("JOURNAL_NODE_HTTP_PORT", "1");
        environmentVariables.set("ZKFC_CPUS", "1.0");
        environmentVariables.set("ZKFC_MEM", "1024");
        environmentVariables.set("NAME_CPUS", "1.0");
        environmentVariables.set("NAME_MEM", "1024");
        environmentVariables.set("NAME_DISK", "1024");
        environmentVariables.set("NAME_DISK_TYPE", "MOUNT");
        environmentVariables.set("NAME_NODE_RPC_PORT", "1");
        environmentVariables.set("NAME_NODE_HTTP_PORT", "1");
        environmentVariables.set("DATA_COUNT", "3");
        environmentVariables.set("DATA_CPUS", "1.0");
        environmentVariables.set("DATA_MEM", "1024");
        environmentVariables.set("DATA_DISK", "1024");
        environmentVariables.set("DATA_DISK_TYPE", "MOUNT");
        environmentVariables.set("DATA_NODE_RPC_PORT", "1");
        environmentVariables.set("DATA_NODE_HTTP_PORT", "1");
        environmentVariables.set("DATA_NODE_IPC_PORT", "1");
        environmentVariables.set("JOURNAL_STRATEGY", "parallel");
        environmentVariables.set("DATA_STRATEGY", "parallel");

        URL resource = ServiceSpecTest.class.getClassLoader().getResource("hdfs-site.xml");
        environmentVariables.set("CONFIG_TEMPLATE_PATH", new File(resource.getPath()).getParent());
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testOneTimePlanDeserialization() throws Exception {
        testDeserialization("hdfs_svc.yml");
    }

    @Test
    public void testOneTimePlanValidation() throws Exception {
        testValidation("hdfs_svc.yml");
    }

    private void testDeserialization(String yamlFileName) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(yamlFileName).getFile());

        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateServiceSpec(generateRawSpecFromYAML(file));
        Assert.assertNotNull(serviceSpec);
        Assert.assertEquals(8080, serviceSpec.getApiPort());
        DefaultServiceSpec.getFactory(serviceSpec, Collections.emptyList());
    }

    private void testValidation(String yamlFileName) throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(yamlFileName).getFile());
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

        ConfigurationUpdater.UpdateResult configUpdateResult = DefaultScheduler.updateConfig(
                serviceSpec, stateStore, configStore);

        OfferRequirementProvider offerRequirementProvider =
                DefaultScheduler.createOfferRequirementProvider(stateStore, configUpdateResult.targetId);

        DefaultScheduler.create(serviceSpec, stateStore, configStore, offerRequirementProvider);
    }
}
