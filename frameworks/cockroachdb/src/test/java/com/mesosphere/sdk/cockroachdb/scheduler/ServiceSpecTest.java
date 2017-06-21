package com.mesosphere.sdk.cockroachdb.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.Test;
import java.net.URL;
import java.io.File;

public class ServiceSpecTest extends BaseServiceSpecTest {

    public ServiceSpecTest() {
        envVars.put("EXECUTOR_URI", "");
        envVars.put("LIBMESOS_URI", "");
        envVars.put("PORT_API", "8080");
        envVars.put("FRAMEWORK_NAME", "cockroachdb");

        envVars.put("NODE_COUNT", "2");
        envVars.put("NODE_CPUS", "0.1");
        envVars.put("NODE_MEM", "512");
        envVars.put("NODE_DISK", "5000");
        envVars.put("NODE_DISK_TYPE", "ROOT");

        envVars.put("SIDE_COUNT", "2");
        envVars.put("SIDE_CPUS", "0.1");
        envVars.put("SIDE_MEM", "512");
        envVars.put("SIDE_DISK", "5000");
        envVars.put("SIDE_DISK_TYPE", "ROOT");

        envVars.put("SLEEP_DURATION", "1000");
        
        URL resource = ServiceSpecTest.class.getClassLoader().getResource("start.sh.mustache");
        envVars.put("CONFIG_TEMPLATE_PATH", new File(resource.getPath()).getParent());
    }

    @Test
    public void testYmlBase() throws Exception {
        testYaml("svc.yml");
    }
}
