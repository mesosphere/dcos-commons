package com.mesosphere.sdk.helloworld.scheduler;

import org.junit.Test;

import com.mesosphere.sdk.testing.ServiceRenderUtils;
import com.mesosphere.sdk.testing.ServiceSpecTestBuilder;
import com.mesosphere.sdk.testing.ServiceSpecTestUtils;

public class ServiceSpecTest {

    @Test
    public void testSpecBase() throws Exception {
        ServiceSpecTestUtils.test("svc.yml");
    }

    @Test
    public void testSpecSimple() throws Exception {
        ServiceSpecTestUtils.test("examples/simple.yml");
    }

    @Test
    public void testSpecPlan() throws Exception {
        ServiceSpecTestUtils.test("examples/plan.yml");
    }

    @Test
    public void testSpecSidecar() throws Exception {
        ServiceSpecTestUtils.test("examples/sidecar.yml");
    }

    @Test
    public void testSpecTaskcfg() throws Exception {
        ServiceSpecTestUtils.test("examples/taskcfg.yml");
    }

    @Test
    public void testSpecUri() throws Exception {
        ServiceSpecTestUtils.test("examples/uri.yml");
    }

    @Test
    public void testSpecWebUrl() throws Exception {
        ServiceSpecTestUtils.test("examples/web-url.yml");
    }

    @Test
    public void testGpuResource() throws Exception {
        ServiceSpecTestUtils.test("examples/gpu_resource.yml");
    }

    @Test
    public void testOverlayNetworks() throws Exception {
        ServiceSpecTestUtils.test("examples/overlay.yml");
    }

    @Test
    public void testSecrets() throws Exception {
        // This yml file expects some additional envvars which aren't in the default marathon.json.mustache,
        // so we need to provide them manually:
        new ServiceSpecTestBuilder(ServiceRenderUtils.getDistFile("examples/secrets.yml"))
                .setCustomEnv(
                        "HELLO_SECRET1", "hello-world/secret1",
                        "HELLO_SECRET2", "hello-world/secret2",
                        "WORLD_SECRET1", "hello-world/secret1",
                        "WORLD_SECRET2", "hello-world/secret2",
                        "WORLD_SECRET3", "hello-world/secret3")
                .test();
    }

    @Test
    public void testPreReservedRole() throws Exception {
        ServiceSpecTestUtils.test("examples/pre-reserved.yml");
    }

    @Test
    public void testMultiStepPlan() throws Exception {
        ServiceSpecTestUtils.test("examples/multistep_plan.yml");
    }

    @Test
    public void testTLS() throws Exception {
        ServiceSpecTestUtils.test("examples/tls.yml");
    }
}
