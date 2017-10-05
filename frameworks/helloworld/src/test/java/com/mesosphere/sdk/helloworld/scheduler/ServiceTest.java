package com.mesosphere.sdk.helloworld.scheduler;

import org.junit.Test;

import com.mesosphere.sdk.testing.ServiceTestRunner;

public class ServiceTest {

    @Test
    public void testSpecBase() throws Exception {
        new ServiceTestRunner().run();
    }

    @Test
    public void testSpecSimple() throws Exception {
        new ServiceTestRunner("examples/simple.yml").run();
    }

    @Test
    public void testSpecPlan() throws Exception {
        new ServiceTestRunner("examples/plan.yml").run();
    }

    @Test
    public void testSpecSidecar() throws Exception {
        new ServiceTestRunner("examples/sidecar.yml").run();
    }

    @Test
    public void testSpecTaskcfg() throws Exception {
        new ServiceTestRunner("examples/taskcfg.yml").run();
    }

    @Test
    public void testSpecUri() throws Exception {
        new ServiceTestRunner("examples/uri.yml").run();
    }

    @Test
    public void testSpecWebUrl() throws Exception {
        new ServiceTestRunner("examples/web-url.yml").run();
    }

    @Test
    public void testGpuResource() throws Exception {
        new ServiceTestRunner("examples/gpu_resource.yml").run();
    }

    @Test
    public void testOverlayNetworks() throws Exception {
        new ServiceTestRunner("examples/overlay.yml").run();
    }

    @Test
    public void testSecrets() throws Exception {
        // This yml file expects some additional envvars which aren't in the default marathon.json.mustache,
        // so we need to provide them manually:
        new ServiceTestRunner("examples/secrets.yml")
                .setSchedulerEnv(
                        "HELLO_SECRET1", "hello-world/secret1",
                        "HELLO_SECRET2", "hello-world/secret2",
                        "WORLD_SECRET1", "hello-world/secret1",
                        "WORLD_SECRET2", "hello-world/secret2",
                        "WORLD_SECRET3", "hello-world/secret3")
                .run();
    }

    @Test
    public void testPreReservedRole() throws Exception {
        new ServiceTestRunner("examples/pre-reserved.yml").run();
    }

    @Test
    public void testMultiStepPlan() throws Exception {
        new ServiceTestRunner("examples/multistep_plan.yml").run();
    }

    @Test
    public void testTLS() throws Exception {
        new ServiceTestRunner("examples/tls.yml").run();
    }
}
