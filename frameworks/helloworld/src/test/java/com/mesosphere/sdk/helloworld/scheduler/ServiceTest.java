package com.mesosphere.sdk.helloworld.scheduler;

import org.junit.Test;

import com.mesosphere.sdk.testing.ServiceTestBuilder;

public class ServiceTest {

    @Test
    public void testSpecBase() throws Exception {
        new ServiceTestBuilder().render();
    }

    @Test
    public void testSpecSimple() throws Exception {
        new ServiceTestBuilder("examples/simple.yml").render();
    }

    @Test
    public void testSpecPlan() throws Exception {
        new ServiceTestBuilder("examples/plan.yml").render();
    }

    @Test
    public void testSpecSidecar() throws Exception {
        new ServiceTestBuilder("examples/sidecar.yml").render();
    }

    @Test
    public void testSpecTaskcfg() throws Exception {
        new ServiceTestBuilder("examples/taskcfg.yml").render();
    }

    @Test
    public void testSpecUri() throws Exception {
        new ServiceTestBuilder("examples/uri.yml").render();
    }

    @Test
    public void testSpecWebUrl() throws Exception {
        new ServiceTestBuilder("examples/web-url.yml").render();
    }

    @Test
    public void testGpuResource() throws Exception {
        new ServiceTestBuilder("examples/gpu_resource.yml").render();
    }

    @Test
    public void testOverlayNetworks() throws Exception {
        new ServiceTestBuilder("examples/overlay.yml").render();
    }

    @Test
    public void testSecrets() throws Exception {
        // This yml file expects some additional envvars which aren't in the default marathon.json.mustache,
        // so we need to provide them manually:
        new ServiceTestBuilder("examples/secrets.yml")
                .setSchedulerEnv(
                        "HELLO_SECRET1", "hello-world/secret1",
                        "HELLO_SECRET2", "hello-world/secret2",
                        "WORLD_SECRET1", "hello-world/secret1",
                        "WORLD_SECRET2", "hello-world/secret2",
                        "WORLD_SECRET3", "hello-world/secret3")
                .render();
    }

    @Test
    public void testPreReservedRole() throws Exception {
        new ServiceTestBuilder("examples/pre-reserved.yml").render();
    }

    @Test
    public void testMultiStepPlan() throws Exception {
        new ServiceTestBuilder("examples/multistep_plan.yml").render();
    }

    @Test
    public void testTLS() throws Exception {
        new ServiceTestBuilder("examples/tls.yml").render();
    }
}
