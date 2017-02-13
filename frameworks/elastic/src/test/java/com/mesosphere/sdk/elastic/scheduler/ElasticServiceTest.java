package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.net.URL;

public class ElasticServiceTest extends BaseServiceSpecTest {

    @BeforeClass
    public static void beforeAll() {
        ENV_VARS.set("ELASTIC_VERSION", "5.x");
        ENV_VARS.set("EXECUTOR_URI", "http://executor.uri");
        ENV_VARS.set("LIBMESOS_URI", "http://lib.mesos.uri");
        ENV_VARS.set("SCHEDULER_URI", "http://scheduler.uri");
        ENV_VARS.set("BOOTSTRAP_URI", "http://bootstrap.uri");
        ENV_VARS.set("PORT_API", "8080");
        ENV_VARS.set("FRAMEWORK_NAME", "elastic");
        ENV_VARS.set("FRAMEWORK_USER", "non-root");
        ENV_VARS.set("PLAN_STRATEGY", "parallel");
        ENV_VARS.set("ELASTICSEARCH_PLUGINS", "analysis-icu");
        ENV_VARS.set("MASTER_NODE_CPUS", "1");
        ENV_VARS.set("MASTER_NODE_MEM", "4000");
        ENV_VARS.set("MASTER_NODE_HEAP_MB", "2000");
        ENV_VARS.set("MASTER_NODE_DISK", "10000");
        ENV_VARS.set("MASTER_NODE_DISK_TYPE", "ROOT");
        ENV_VARS.set("MASTER_NODE_TRANSPORT_PORT", "9300");
        ENV_VARS.set("DATA_NODE_COUNT", "2");
        ENV_VARS.set("DATA_NODE_CPUS", "1");
        ENV_VARS.set("DATA_NODE_MEM", "4000");
        ENV_VARS.set("DATA_NODE_HEAP_MB", "2000");
        ENV_VARS.set("DATA_NODE_DISK", "10000");
        ENV_VARS.set("DATA_NODE_DISK_TYPE", "ROOT");
        ENV_VARS.set("INGEST_NODE_COUNT", "1");
        ENV_VARS.set("INGEST_NODE_CPUS", "1");
        ENV_VARS.set("INGEST_NODE_MEM", "4000");
        ENV_VARS.set("INGEST_NODE_HEAP_MB", "2000");
        ENV_VARS.set("INGEST_NODE_DISK", "10000");
        ENV_VARS.set("INGEST_NODE_DISK_TYPE", "ROOT");
        ENV_VARS.set("COORDINATOR_NODE_COUNT", "1");
        ENV_VARS.set("COORDINATOR_NODE_CPUS", "1");
        ENV_VARS.set("COORDINATOR_NODE_MEM", "4000");
        ENV_VARS.set("COORDINATOR_NODE_HEAP_MB", "2000");
        ENV_VARS.set("COORDINATOR_NODE_DISK", "10000");
        ENV_VARS.set("COORDINATOR_NODE_DISK_TYPE", "ROOT");
        ENV_VARS.set("KIBANA_COUNT", "1");
        ENV_VARS.set("KIBANA_CPUS", "1");
        ENV_VARS.set("KIBANA_MEM", "2000");
        ENV_VARS.set("KIBANA_DISK", "2000");
        ENV_VARS.set("KIBANA_DISK_TYPE", "ROOT");
        ENV_VARS.set("KIBANA_PASSWORD", "changeme");
        ENV_VARS.set("PROXYLITE_CPUS", ".1");
        ENV_VARS.set("PROXYLITE_MEM", "32");
        ENV_VARS.set("PROXYLITE_PORT", "4040");
        URL resource = ElasticServiceTest.class.getClassLoader().getResource("elastic_service.yml");
        ENV_VARS.set("CONFIG_TEMPLATE_PATH", new File(resource.getPath()).getParent());
    }

    @Test
    public void testYaml() throws Exception {
        super.testYaml("elastic_service.yml");
    }

}