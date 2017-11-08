package com.mesosphere.sdk.helloworld.scheduler;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Test;

import com.mesosphere.sdk.testing.Expect;
import com.mesosphere.sdk.testing.Send;
import com.mesosphere.sdk.testing.ServiceTestRunner;
import com.mesosphere.sdk.testing.SimulationTick;

/**
 * Tests for the hello world service and its example yml files.
 */
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
    public void testExampleSpecs() throws Exception {
        // Some example files may require additional custom scheduler envvars:
        Map<String, Map<String, String>> schedulerEnvForExamples = new HashMap<>();
        schedulerEnvForExamples.put("secrets.yml", toMap(
                "HELLO_SECRET1", "hello-world/secret1",
                "HELLO_SECRET2", "hello-world/secret2",
                "WORLD_SECRET1", "hello-world/secret1",
                "WORLD_SECRET2", "hello-world/secret2",
                "WORLD_SECRET3", "hello-world/secret3"));

        // Iterate over yml files in dist/examples/, run sanity check for each:
        File[] exampleFiles = ServiceTestRunner.getDistFile("examples").listFiles();
        Assert.assertNotNull(exampleFiles);
        Assert.assertTrue(exampleFiles.length != 0);
        for (File examplesFile : exampleFiles) {
            ServiceTestRunner serviceTestRunner = new ServiceTestRunner(examplesFile);
            Map<String, String> schedulerEnv = schedulerEnvForExamples.get(examplesFile.getName());
            if (schedulerEnv != null) {
                serviceTestRunner.setSchedulerEnv(schedulerEnv);
            }
            try {
                serviceTestRunner.run();
            } catch (Exception e) {
                throw new Exception(String.format(
                        "Failed to render %s: %s", examplesFile.getAbsolutePath(), e.getMessage()), e);
            }
        }
    }

    private static Map<String, String> toMap(String... keyVals) {
        Map<String, String> map = new HashMap<>();
        if (keyVals.length % 2 != 0) {
            throw new IllegalArgumentException(String.format(
                    "Expected an even number of arguments [key, value, key, value, ...], got: %d",
                    keyVals.length));
        }
        for (int i = 0; i < keyVals.length; i += 2) {
            map.put(keyVals[i], keyVals[i + 1]);
        }
        return map;
    }
}
