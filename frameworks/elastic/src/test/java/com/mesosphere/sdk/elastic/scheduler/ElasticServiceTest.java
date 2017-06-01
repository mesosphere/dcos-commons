package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.Test;

public class ElasticServiceTest extends BaseServiceSpecTest {

    public ElasticServiceTest() {
        super(
                "ELASTIC_VERSION", "5.x",
                "EXECUTOR_URI", "http://executor.uri",
                "LIBMESOS_URI", "http://lib.mesos.uri",
                "SCHEDULER_URI", "http://scheduler.uri",
                "BOOTSTRAP_URI", "http://bootstrap.uri",
                "PORT_API", "8080",
                "FRAMEWORK_NAME", "elastic",
                "FRAMEWORK_USER", "non-root",
                "PLAN_STRATEGY", "parallel",
                "TASKCFG_ALL_ELASTICSEARCH_PLUGINS", "analysis-icu",
                "TASKCFG_ALL_XPACK_ENABLED", "false",
                "ELASTICSEARCH_HEALTH_USER", "elastic",
                "ELASTICSEARCH_HEALTH_USER_PASSWORD", "changeme",
                "MASTER_NODE_CPUS", "1",
                "MASTER_NODE_MEM", "4000",
                "MASTER_NODE_HEAP_MB", "2000",
                "MASTER_NODE_DISK", "10000",
                "MASTER_NODE_DISK_TYPE", "ROOT",
                "MASTER_NODE_TRANSPORT_PORT", "9300",
                "DATA_NODE_COUNT", "2",
                "DATA_NODE_CPUS", "1",
                "DATA_NODE_MEM", "4000",
                "DATA_NODE_HEAP_MB", "2000",
                "DATA_NODE_DISK", "10000",
                "DATA_NODE_DISK_TYPE", "ROOT",
                "INGEST_NODE_COUNT", "1",
                "INGEST_NODE_CPUS", "1",
                "INGEST_NODE_MEM", "4000",
                "INGEST_NODE_HEAP_MB", "2000",
                "INGEST_NODE_DISK", "10000",
                "INGEST_NODE_DISK_TYPE", "ROOT",
                "COORDINATOR_NODE_COUNT", "1",
                "COORDINATOR_NODE_CPUS", "1",
                "COORDINATOR_NODE_MEM", "4000",
                "COORDINATOR_NODE_HEAP_MB", "2000",
                "COORDINATOR_NODE_DISK", "10000",
                "COORDINATOR_NODE_DISK_TYPE", "ROOT");
    }

    @Test
    public void testYaml() throws Exception {
        super.testYaml("svc.yml");
    }

}
