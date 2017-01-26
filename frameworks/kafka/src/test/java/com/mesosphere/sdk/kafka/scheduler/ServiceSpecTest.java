package com.mesosphere.sdk.kafka.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.BeforeClass;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {
    @BeforeClass
    public static void beforeAll() {
        ENV_VARS.set("EXECUTOR_URI", "");
        ENV_VARS.set("LIBMESOS_URI", "");
        ENV_VARS.set("PORT_API", "8080");
        ENV_VARS.set("BROKER_COUNT", "2");
        ENV_VARS.set("BROKER_CPUS", "0.1");
        ENV_VARS.set("BROKER_MEM", "512");
        ENV_VARS.set("BROKER_DISK", "5000");
    }

    @Test
    public void testYaml() throws Exception {
        super.testYaml("svc.yml");
    }
}