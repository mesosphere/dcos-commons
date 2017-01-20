package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.scheduler.DefaultScheduler;
import com.mesosphere.sdk.specification.DefaultServiceSpec;
import com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory;
import com.mesosphere.sdk.state.StateStoreCache;
import org.apache.curator.test.TestingServer;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import java.io.File;
import java.net.URL;
import java.util.Collections;

import static com.mesosphere.sdk.specification.yaml.YAMLServiceSpecFactory.generateRawSpecFromYAML;

public class ElasticServiceTest {
    @ClassRule
    public static final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @BeforeClass
    public static void beforeAll() {
        environmentVariables.set("ELASTIC_VERSION", "5");
        environmentVariables.set("EXECUTOR_URI", "http://executor.uri");
        environmentVariables.set("LIBMESOS_URI", "http://lib.mesos.uri");
        environmentVariables.set("SCHEDULER_URI", "http://scheduler.uri");
        environmentVariables.set("PORT_API", "8080");
        environmentVariables.set("SERVICE_NAME", "elastic");
        environmentVariables.set("FRAMEWORK_USER", "non-root");
        environmentVariables.set("PLAN_STRATEGY", "parallel");
        environmentVariables.set("ELASTICSEARCH_PLUGINS", "analysis-icu");
        environmentVariables.set("MASTER_NODE_CPUS", "1");
        environmentVariables.set("MASTER_NODE_MEM", "4000");
        environmentVariables.set("MASTER_NODE_HEAP_MB", "2000");
        environmentVariables.set("MASTER_NODE_DISK", "10000");
        environmentVariables.set("MASTER_NODE_DISK_TYPE", "ROOT");
        environmentVariables.set("MASTER_NODE_TRANSPORT_PORT", "9300");
        environmentVariables.set("DATA_NODE_COUNT", "2");
        environmentVariables.set("DATA_NODE_CPUS", "1");
        environmentVariables.set("DATA_NODE_MEM", "4000");
        environmentVariables.set("DATA_NODE_HEAP_MB", "2000");
        environmentVariables.set("DATA_NODE_DISK", "10000");
        environmentVariables.set("DATA_NODE_DISK_TYPE", "ROOT");
        environmentVariables.set("INGEST_NODE_COUNT", "1");
        environmentVariables.set("INGEST_NODE_CPUS", "1");
        environmentVariables.set("INGEST_NODE_MEM", "4000");
        environmentVariables.set("INGEST_NODE_HEAP_MB", "2000");
        environmentVariables.set("INGEST_NODE_DISK", "10000");
        environmentVariables.set("INGEST_NODE_DISK_TYPE", "ROOT");
        environmentVariables.set("COORDINATOR_NODE_COUNT", "1");
        environmentVariables.set("COORDINATOR_NODE_CPUS", "1");
        environmentVariables.set("COORDINATOR_NODE_MEM", "4000");
        environmentVariables.set("COORDINATOR_NODE_HEAP_MB", "2000");
        environmentVariables.set("COORDINATOR_NODE_DISK", "10000");
        environmentVariables.set("COORDINATOR_NODE_DISK_TYPE", "ROOT");
        environmentVariables.set("KIBANA_COUNT", "1");
        environmentVariables.set("KIBANA_CPUS", "1");
        environmentVariables.set("KIBANA_MEM", "2000");
        environmentVariables.set("KIBANA_DISK", "2000");
        environmentVariables.set("KIBANA_DISK_TYPE", "ROOT");
        environmentVariables.set("KIBANA_PASSWORD", "changeme");
        environmentVariables.set("PROXYLITE_CPUS", ".1");
        environmentVariables.set("PROXYLITE_MEM", "32");
        environmentVariables.set("PROXYLITE_PORT", "4040");
        URL resource = ElasticServiceTest.class.getClassLoader().getResource("elastic_service.yml");
        environmentVariables.set("CONFIG_TEMPLATE_PATH", new File(resource.getPath()).getParent());
    }


    @Test
    public void testOneTimePlanDeserialization() throws Exception {
        testDeserialization("elastic_service.yml");
    }

    @Ignore("Blocked on https://mesosphere.atlassian.net/browse/INFINITY-848")
    @Test
    public void testOneTimePlanValidation() throws Exception {
        testValidation("elastic_service.yml");
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
        File file = new File(getClass().getClassLoader().getResource(yamlFileName).getFile());
        DefaultServiceSpec serviceSpec = YAMLServiceSpecFactory
                .generateServiceSpec(generateRawSpecFromYAML(file));

        TestingServer testingServer = new TestingServer();
        StateStoreCache.resetInstanceForTests();
        DefaultScheduler.newBuilder(serviceSpec)
                .setStateStore(DefaultScheduler.createStateStore(serviceSpec, testingServer.getConnectString()))
                .setConfigStore(DefaultScheduler.createConfigStore(serviceSpec, testingServer.getConnectString()))
                .build();
        testingServer.close();
    }
}