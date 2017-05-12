package com.mesosphere.sdk.helloworld.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.BeforeClass;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {

    @BeforeClass
    public static void beforeAll() {
        ENV_VARS.set("EXECUTOR_URI", "");
        ENV_VARS.set("LIBMESOS_URI", "");
        ENV_VARS.set("PORT_API", "8080");

        ENV_VARS.set("SLEEP_DURATION", "1000");
        ENV_VARS.set("HELLO_COUNT", "2");
        ENV_VARS.set("HELLO_PORT", "4444");
        ENV_VARS.set("HELLO_VIP_NAME", "helloworld");
        ENV_VARS.set("HELLO_VIP_PORT", "9999");
        ENV_VARS.set("HELLO_CPUS", "0.1");
        ENV_VARS.set("HELLO_MEM", "512");
        ENV_VARS.set("HELLO_DISK", "5000");

        ENV_VARS.set("WORLD_COUNT", "3");
        ENV_VARS.set("WORLD_CPUS", "0.2");
        ENV_VARS.set("WORLD_MEM", "1024");
        ENV_VARS.set("WORLD_FAILS", "3");
        ENV_VARS.set("WORLD_DISK", "5000");
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

   /* @Test
    public void testSecrets() throws Exception {
        testYaml("examples/secrets.yml");
    }*/
}
