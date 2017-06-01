package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {

    public ServiceSpecTest() {
        super(
                "EXECUTOR_URI", "http://executor.uri",
                "BOOTSTRAP_URI", "http://bootstrap.uri",
                "SCHEDULER_URI", "http://scheduler.uri",
                "LIBMESOS_URI", "http://libmesos.uri",
                "PORT_API", "8080",

                "SERVICE_NAME", "cassandra",
                "TASKCFG_ALL_CASSANDRA_CLUSTER_NAME", "cassandra",
                "NODES", "3",
                "SERVICE_USER", "core",
                "SERVICE_ROLE", "role",
                "SERVICE_PRINCIPAL", "principal",
                "CASSANDRA_CPUS", "0.1",
                "CASSANDRA_VERSION", "3.0.13",
                "CASSANDRA_MEMORY_MB", "512",
                "TASKCFG_ALL_JMX_PORT", "9000",
                "TASKCFG_ALL_CASSANDRA_STORAGE_PORT", "9001",
                "TASKCFG_ALL_CASSANDRA_SSL_STORAGE_PORT", "9002",
                "TASKCFG_ALL_CASSANDRA_NATIVE_TRANSPORT_PORT", "9003",
                "TASKCFG_ALL_CASSANDRA_RPC_PORT", "9004",
                "TASKCFG_ALL_CASSANDRA_HEAP_SIZE_MB", "4000",
                "TASKCFG_ALL_CASSANDRA_HEAP_NEW_MB", "400",
                "CASSANDRA_HEAP_GC", "CMS",
                "CASSANDRA_DISK_MB", "5000",
                "CASSANDRA_DISK_TYPE", "ROOT");
    }

    @Test
    public void testYaml() throws Exception {
        super.testYaml("svc.yml");
    }
}
