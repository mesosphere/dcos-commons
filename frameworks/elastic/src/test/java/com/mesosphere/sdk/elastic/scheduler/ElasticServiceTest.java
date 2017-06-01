package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.net.URL;

public class ElasticServiceTest extends BaseServiceSpecTest {

    @BeforeClass
    public static void beforeAll() {
        ENV_VARS.put("ELASTIC_VERSION", "5.x");
        ENV_VARS.put("EXECUTOR_URI", "http://executor.uri");
        ENV_VARS.put("LIBMESOS_URI", "http://lib.mesos.uri");
        ENV_VARS.put("SCHEDULER_URI", "http://scheduler.uri");
        ENV_VARS.put("BOOTSTRAP_URI", "http://bootstrap.uri");
        ENV_VARS.put("PORT_API", "8080");
        ENV_VARS.put("FRAMEWORK_NAME", "elastic");
        ENV_VARS.put("FRAMEWORK_USER", "non-root");
        ENV_VARS.put("PLAN_STRATEGY", "parallel");
        ENV_VARS.put("TASKCFG_ALL_ELASTICSEARCH_PLUGINS", "analysis-icu");
        ENV_VARS.put("TASKCFG_ALL_XPACK_ENABLED", "false");
        ENV_VARS.put("ELASTICSEARCH_HEALTH_USER", "elastic");
        ENV_VARS.put("ELASTICSEARCH_HEALTH_USER_PASSWORD", "changeme");
        ENV_VARS.put("MASTER_NODE_CPUS", "1");
        ENV_VARS.put("MASTER_NODE_MEM", "4000");
        ENV_VARS.put("MASTER_NODE_HEAP_MB", "2000");
        ENV_VARS.put("MASTER_NODE_DISK", "10000");
        ENV_VARS.put("MASTER_NODE_DISK_TYPE", "ROOT");
        ENV_VARS.put("MASTER_NODE_TRANSPORT_PORT", "9300");
        ENV_VARS.put("DATA_NODE_COUNT", "2");
        ENV_VARS.put("DATA_NODE_CPUS", "1");
        ENV_VARS.put("DATA_NODE_MEM", "4000");
        ENV_VARS.put("DATA_NODE_HEAP_MB", "2000");
        ENV_VARS.put("DATA_NODE_DISK", "10000");
        ENV_VARS.put("DATA_NODE_DISK_TYPE", "ROOT");
        ENV_VARS.put("INGEST_NODE_COUNT", "1");
        ENV_VARS.put("INGEST_NODE_CPUS", "1");
        ENV_VARS.put("INGEST_NODE_MEM", "4000");
        ENV_VARS.put("INGEST_NODE_HEAP_MB", "2000");
        ENV_VARS.put("INGEST_NODE_DISK", "10000");
        ENV_VARS.put("INGEST_NODE_DISK_TYPE", "ROOT");
        ENV_VARS.put("COORDINATOR_NODE_COUNT", "1");
        ENV_VARS.put("COORDINATOR_NODE_CPUS", "1");
        ENV_VARS.put("COORDINATOR_NODE_MEM", "4000");
        ENV_VARS.put("COORDINATOR_NODE_HEAP_MB", "2000");
        ENV_VARS.put("COORDINATOR_NODE_DISK", "10000");
        ENV_VARS.put("COORDINATOR_NODE_DISK_TYPE", "ROOT");
        URL resource = ElasticServiceTest.class.getClassLoader().getResource("svc.yml");
        ENV_VARS.put("CONFIG_TEMPLATE_PATH", new File(resource.getPath()).getParent());
    }

    @Test
    public void testYaml() throws Exception {
        super.testYaml("svc.yml");
    }

}
