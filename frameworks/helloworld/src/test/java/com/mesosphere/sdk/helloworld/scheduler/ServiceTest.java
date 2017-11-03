package com.mesosphere.sdk.helloworld.scheduler;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.mesos.Protos;
import org.junit.Test;

import com.mesosphere.sdk.testing.Expect;
import com.mesosphere.sdk.testing.Send;
import com.mesosphere.sdk.testing.ServiceTestRunner;
import com.mesosphere.sdk.testing.SimulationTick;

public class ServiceTest {

    @Test
    public void testSpecBase() throws Exception {
        Collection<SimulationTick> ticks = new ArrayList<>();

        ticks.add(Send.register());

        ticks.add(Expect.reconciledImplicitly());

        // Verify that service launches 1 hello pod then 2 world pods.
        ticks.add(Send.offerBuilder("hello").build());
        ticks.add(Expect.launchedTasks("hello-0-server"));

        // Send another offer before hello-0 is finished:
        ticks.add(Send.offerBuilder("world").build());
        ticks.add(Expect.declinedLastOffer());

        // Running, no readiness check is applicable:
        ticks.add(Send.taskStatus("hello-0-server", Protos.TaskState.TASK_RUNNING).build());

        // Now world-0 will deploy:
        ticks.add(Send.offerBuilder("world").build());
        ticks.add(Expect.launchedTasks("world-0-server"));

        // world-0 has a readiness check, so the scheduler is waiting for that:
        ticks.add(Send.taskStatus("world-0-server", Protos.TaskState.TASK_RUNNING).build());
        ticks.add(Send.offerBuilder("world").build());
        ticks.add(Expect.declinedLastOffer());

        // With world-0's readiness check passing, world-1 still won't launch due to a hostname placement constraint:
        ticks.add(Send.taskStatus("world-0-server", Protos.TaskState.TASK_RUNNING).setReadinessCheckExitCode(0).build());
        ticks.add(Send.offerBuilder("world").build());
        ticks.add(Expect.declinedLastOffer());

        // world-1 will finally launch if the offered hostname is different:
        ticks.add(Send.offerBuilder("world").setHostname("host-foo").build());
        ticks.add(Expect.launchedTasks("world-1-server"));
        ticks.add(Send.taskStatus("world-1-server", Protos.TaskState.TASK_RUNNING).setReadinessCheckExitCode(0).build());

        // No more worlds to launch:
        ticks.add(Send.offerBuilder("world").setHostname("host-bar").build());
        ticks.add(Expect.declinedLastOffer());

        ticks.add(Expect.allPlansComplete());

        new ServiceTestRunner().run(ticks);
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
