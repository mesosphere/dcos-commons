package com.mesosphere.sdk.template.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {

    public ServiceSpecTest() {
        super(
                "EXECUTOR_URI", "",
                "LIBMESOS_URI", "",
                "PORT_API", "8080",
                "FRAMEWORK_NAME", "template",

                "NODE_COUNT", "2",
                "NODE_CPUS", "0.1",
                "NODE_MEM", "512",
                "NODE_DISK", "5000",
                "NODE_DISK_TYPE", "ROOT",

                "SLEEP_DURATION", "1000");
    }

    @Test
    public void testYmlBase() throws Exception {
        testYaml("svc.yml");
    }
}
