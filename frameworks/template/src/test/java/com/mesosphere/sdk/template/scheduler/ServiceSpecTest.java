package com.mesosphere.sdk.template.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.BeforeClass;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {

    @BeforeClass
    public static void beforeAll() {
        ENV_VARS.put("EXECUTOR_URI", "");
        ENV_VARS.put("LIBMESOS_URI", "");
        ENV_VARS.put("PORT_API", "8080");
        ENV_VARS.put("FRAMEWORK_NAME", "template");

        ENV_VARS.put("NODE_COUNT", "2");
        ENV_VARS.put("NODE_CPUS", "0.1");
        ENV_VARS.put("NODE_MEM", "512");
        ENV_VARS.put("NODE_DISK", "5000");
        ENV_VARS.put("NODE_DISK_TYPE", "ROOT");

        ENV_VARS.put("SLEEP_DURATION", "1000");
    }

    @Test
    public void testYmlBase() throws Exception {
        testYaml("svc.yml");
    }
}
