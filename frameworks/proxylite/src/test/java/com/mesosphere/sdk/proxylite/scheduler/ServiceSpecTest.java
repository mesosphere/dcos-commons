package com.mesosphere.sdk.proxylite.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.BeforeClass;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {

    @BeforeClass
    public static void beforeAll() {
        ENV_VARS.set("EXECUTOR_URI", "");
        ENV_VARS.set("LIBMESOS_URI", "");
        ENV_VARS.set("PORT_API", "8080");
        ENV_VARS.set("FRAMEWORK_NAME", "proxylite");

        ENV_VARS.set("PROXYLITE_COUNT", "2");
        ENV_VARS.set("PROXYLITE_CPUS", "0.1");
        ENV_VARS.set("PROXYLITE_MEM", "512");
        ENV_VARS.set("PROXYLITE_DISK", "5000");

        ENV_VARS.set("SLEEP_DURATION", "1000");
    }

    @Test
    public void testYmlBase() throws Exception {
        testYaml("svc.yml");
    }
}
