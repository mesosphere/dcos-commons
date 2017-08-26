package com.mesosphere.sdk.helloworld.scheduler;

import com.mesosphere.sdk.testing.BaseServiceSpecTest;
import org.junit.Test;

public class ServiceSpecTest extends BaseServiceSpecTest {

    public ServiceSpecTest() {
        super();
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
    public void testGpuResource() throws Exception {
        testYaml("examples/gpu_resource.yml");
    }

    @Test
    public void testOverlayNetworks() throws Exception {
        testYaml("examples/overlay.yml");
    }

    @Test
    public void testSecrets() throws Exception {
        testYaml("examples/secrets.yml");
    }

    @Test
    public void testPreReservedRole() throws Exception {
        testYaml("examples/pre-reserved.yml");
    }

    @Test
    public void testMultiStepPlan() throws Exception {
        testYaml("examples/multistep_plan.yml");
    }

    @Test
    public void testTLS() throws Exception {
        testYaml("examples/tls.yml");
    }
}
