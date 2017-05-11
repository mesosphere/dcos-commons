package com.mesosphere.sdk.cassandra.scheduler;

import com.mesosphere.sdk.specification.DefaultService;
import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.net.URL;

public class ServiceSpecTest extends BaseServiceSpecTest {
    @BeforeClass
    public static void beforeAll() {
        ENV_VARS.set("EXECUTOR_URI", "http://executor.uri");
        ENV_VARS.set("BOOTSTRAP_URI", "http://bootstrap.uri");
        ENV_VARS.set("SCHEDULER_URI", "http://scheduler.uri");
        ENV_VARS.set("LIBMESOS_URI", "http://libmesos.uri");
        ENV_VARS.set("PORT_API", "8080");

        ENV_VARS.set("SERVICE_NAME", "cassandra");
        ENV_VARS.set("TASKCFG_ALL_CASSANDRA_CLUSTER_NAME", "cassandra");
        ENV_VARS.set("NODES", "3");
        ENV_VARS.set("SERVICE_USER", "core");
        ENV_VARS.set("SERVICE_ROLE", "role");
        ENV_VARS.set("SERVICE_PRINCIPAL", "principal");
        ENV_VARS.set("CASSANDRA_CPUS", "0.1");
        ENV_VARS.set("CASSANDRA_VERSION", "3.0.13");
        ENV_VARS.set("CASSANDRA_MEMORY_MB", "512");
        ENV_VARS.set("TASKCFG_ALL_JMX_PORT", "9000");
        ENV_VARS.set("TASKCFG_ALL_CASSANDRA_STORAGE_PORT", "9001");
        ENV_VARS.set("TASKCFG_ALL_CASSANDRA_SSL_STORAGE_PORT", "9002");
        ENV_VARS.set("TASKCFG_ALL_CASSANDRA_NATIVE_TRANSPORT_PORT", "9003");
        ENV_VARS.set("TASKCFG_ALL_CASSANDRA_RPC_PORT", "9004");
        ENV_VARS.set("TASKCFG_ALL_CASSANDRA_HEAP_SIZE_MB", "4000");
        ENV_VARS.set("TASKCFG_ALL_CASSANDRA_HEAP_NEW_MB", "400");
        ENV_VARS.set("CASSANDRA_HEAP_GC", "CMS");
        ENV_VARS.set("CASSANDRA_DISK_MB", "5000");
        ENV_VARS.set("CASSANDRA_DISK_TYPE", "ROOT");
        URL resource = DefaultService.class.getClassLoader().getResource("svc.yml");
        ENV_VARS.set("CONFIG_TEMPLATE_PATH", new File(resource.getPath()).getParent());
    }

    @Test
    public void testYaml() throws Exception {
        super.testYaml("svc.yml");
    }
}
