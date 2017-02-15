package com.mesosphere.sdk.spark.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.BeforeClass;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {

    @BeforeClass
    public static void beforeAll() {
        ENV_VARS.set("EXECUTOR_URI", "");
        ENV_VARS.set("LIBMESOS_URI", "");
        ENV_VARS.set("PORT_API", "8080");
        ENV_VARS.set("FRAMEWORK_NAME", "spark");

        ENV_VARS.set("DAEMON_CPUS", "1.0");
        ENV_VARS.set("DAEMON_MEM", "1024");
        ENV_VARS.set("SPARK_MASTER_PORT", "7070");
        ENV_VARS.set("SPARK_MASTER_WEBUI_PORT", "4040");
        ENV_VARS.set("SPARK_VERSION", "4040");
        ENV_VARS.set("WORKER_COUNT", "2");
        ENV_VARS.set("WORKER_CPUS", "1.0");
        ENV_VARS.set("WORKER_MEM", "1024");
        ENV_VARS.set("WORKER_DISK", "1024");
        ENV_VARS.set("SPARK_URI", "spark.tar.gz");
        ENV_VARS.set("SPARK_WORKER_PORT", "6060");
        ENV_VARS.set("SPARK_WORKER_WEBUI_PORT", "4041");
        ENV_VARS.set("SPARK_MASTER_OPTS", "");
        ENV_VARS.set("SPARK_WORKER_OPTS", "");
        ENV_VARS.set("PROXYLITE_CPUS", "1.0");
        ENV_VARS.set("PROXYLITE_MEM", "256");
        ENV_VARS.set("PROXYLITE_PORT", "4040");
    }

    @Test
    public void testYmlBase() throws Exception {
        testYaml("svc.yml");
    }
}
