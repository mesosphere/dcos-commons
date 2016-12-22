package com.mesosphere.sdk.elastic.scheduler;

import org.apache.curator.test.TestingServer;
import org.apache.mesos.Protos;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ElasticServiceTest {
    @ClassRule
    public static final EnvironmentVariables environmentVariables = new EnvironmentVariables();
    private ClassLoader classLoader = getClass().getClassLoader();
    private File file = new File(classLoader.getResource("elastic_service.yml").getFile());
    @Spy
    private ElasticService elasticService = new ElasticService(file);

    @BeforeClass
    public static void beforeAll() {
        environmentVariables.set("ELASTIC_VERSION", "5");
        environmentVariables.set("EXECUTOR_URI", "");
        environmentVariables.set("LIBMESOS_URI", "");
        environmentVariables.set("PORT0", "8080");
        environmentVariables.set("SERVICE_NAME", "elastic");
        environmentVariables.set("FRAMEWORK_USER", "non-root");
        environmentVariables.set("PLAN_STRATEGY", "parallel");
        environmentVariables.set("ELASTICSEARCH_PLUGINS", "analysis-icu");
        environmentVariables.set("MASTER_NODE_CPUS", "1");
        environmentVariables.set("MASTER_NODE_MEM", "4000");
        environmentVariables.set("MASTER_NODE_HEAP_MB", "2000");
        environmentVariables.set("MASTER_NODE_DISK", "10000");
        environmentVariables.set("MASTER_NODE_DISK_TYPE", "ROOT");
        environmentVariables.set("MASTER_NODE_HTTP_PORT", "9200");
        environmentVariables.set("MASTER_NODE_TRANSPORT_PORT", "9300");
        environmentVariables.set("DATA_NODE_COUNT", "2");
        environmentVariables.set("DATA_NODE_CPUS", "1");
        environmentVariables.set("DATA_NODE_MEM", "4000");
        environmentVariables.set("DATA_NODE_HEAP_MB", "2000");
        environmentVariables.set("DATA_NODE_DISK", "10000");
        environmentVariables.set("DATA_NODE_DISK_TYPE", "ROOT");
        environmentVariables.set("DATA_NODE_HTTP_PORT", "9201");
        environmentVariables.set("DATA_NODE_TRANSPORT_PORT", "9301");
        environmentVariables.set("INGEST_NODE_COUNT", "1");
        environmentVariables.set("INGEST_NODE_CPUS", "1");
        environmentVariables.set("INGEST_NODE_MEM", "4000");
        environmentVariables.set("INGEST_NODE_HEAP_MB", "2000");
        environmentVariables.set("INGEST_NODE_DISK", "10000");
        environmentVariables.set("INGEST_NODE_DISK_TYPE", "ROOT");
        environmentVariables.set("INGEST_NODE_HTTP_PORT", "9202");
        environmentVariables.set("INGEST_NODE_TRANSPORT_PORT", "9302");
        environmentVariables.set("COORDINATOR_NODE_COUNT", "1");
        environmentVariables.set("COORDINATOR_NODE_CPUS", "1");
        environmentVariables.set("COORDINATOR_NODE_MEM", "4000");
        environmentVariables.set("COORDINATOR_NODE_HEAP_MB", "2000");
        environmentVariables.set("COORDINATOR_NODE_DISK", "10000");
        environmentVariables.set("COORDINATOR_NODE_DISK_TYPE", "ROOT");
        environmentVariables.set("COORDINATOR_NODE_HTTP_PORT", "9203");
        environmentVariables.set("COORDINATOR_NODE_TRANSPORT_PORT", "9303");
        environmentVariables.set("KIBANA_COUNT", "1");
        environmentVariables.set("KIBANA_CPUS", "1");
        environmentVariables.set("KIBANA_MEM", "2000");
        environmentVariables.set("KIBANA_DISK", "2000");
        environmentVariables.set("KIBANA_DISK_TYPE", "ROOT");
        environmentVariables.set("KIBANA_PORT", "5601");
        environmentVariables.set("KIBANA_PASSWORD", "changeme");
        URL resource = ElasticServiceTest.class.getClassLoader().getResource("elastic_service.yml");
        environmentVariables.set("CONFIG_TEMPLATE_PATH", new File(resource.getPath()).getParent());
    }

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testInitialization() throws Exception {
        TestingServer testingServer = new TestingServer();
        Mockito.doReturn(testingServer.getConnectString()).when(elasticService).getZookeeperConnection();
        elasticService.init();

        Assert.assertNotNull(elasticService);
        Protos.FrameworkInfo frameworkInfo = elasticService.getFrameworkInfo();
        Assert.assertEquals("http://kibana-0-server.elastic.mesos:5601", frameworkInfo.getWebuiUrl());
        Set<String> validators = elasticService.getValidators().stream().map(v -> v.getClass().getSimpleName())
                .collect(Collectors.toSet());
        Set<String> expectedValidators = new HashSet<>(Arrays.asList("PodSpecsCannotShrink",
                "HeapCannotExceedHalfMem", "MasterTransportPortCannotChange", "TaskVolumesCannotChange"));
        Assert.assertEquals(expectedValidators, validators);
    }

}