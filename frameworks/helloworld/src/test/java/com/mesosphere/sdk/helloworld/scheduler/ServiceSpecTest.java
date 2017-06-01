package com.mesosphere.sdk.helloworld.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {

    public ServiceSpecTest() {
        super(
                "FRAMEWORK_NAME", "hello-world",
                "EXECUTOR_URI", "",
                "LIBMESOS_URI", "",
                "PORT_API", "8080",

                "SLEEP_DURATION", "1000",
                "HELLO_COUNT", "2",
                "HELLO_PORT", "4444",
                "HELLO_VIP_NAME", "helloworld",
                "HELLO_VIP_PORT", "9999",
                "HELLO_CPUS", "0.1",
                "HELLO_MEM", "512",
                "HELLO_DISK", "5000",

                "WORLD_COUNT", "3",
                "WORLD_CPUS", "0.2",
                "WORLD_MEM", "1024",
                "WORLD_FAILS", "3",
                "WORLD_DISK", "5000");
    }

    @Test
    public void testYmlBase() throws Exception {
        testYaml("svc.yml");
    }

    @Test
    public void testYmlSimple() throws Exception {
        testYaml("examples/simple.yml");
    }

    @Test
    public void testYmlPlan() throws Exception {
        testYaml("examples/plan.yml");
    }

    @Test
    public void testYmlSidecar() throws Exception {
        testYaml("examples/sidecar.yml");
    }

    @Test
    public void testYmlTaskcfg() throws Exception {
        testYaml("examples/taskcfg.yml");
    }

    @Test
    public void testYmlUri() throws Exception {
        testYaml("examples/uri.yml");
    }

    @Test
    public void testYmlWebUrl() throws Exception {
        testYaml("examples/web-url.yml");
    }

    @Test
    public void testNetwork() throws Exception {
        testYaml("examples/cni.yml");
    }

    @Test
    public void testGpuResource() throws Exception {
        testYaml("examples/gpu_resource.yml");
    }

}
