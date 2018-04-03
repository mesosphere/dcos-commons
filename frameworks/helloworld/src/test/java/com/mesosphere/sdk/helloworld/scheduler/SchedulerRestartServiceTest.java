package com.mesosphere.sdk.helloworld.scheduler;

import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.testing.*;
import org.apache.mesos.Protos;
import org.junit.After;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collection;


public class SchedulerRestartServiceTest {
    @After
    public void afterTest() {
        Mockito.validateMockitoUsage();
    }

    @Test
    public void startedTaskIsPendingAfterRestart_DefaultExecutor() throws Exception {
        // A task that fails its readiness check (exit_code=1), should remain in the PENDING state on restart
        testTaskWithReadinessCheckHasStatus(1, Status.PENDING, true);
    }

    @Test
    public void completeTaskIsCompleteAfterRestart_DefaultExecutor() throws Exception {
        // A task that succeeds its readiness check (exit_code=0), should remain in the COMPLETE state on restart
        testTaskWithReadinessCheckHasStatus(0, Status.COMPLETE, true);
    }

    @Test
    public void startedTaskIsPendingAfterRestart_CustomExecutor() throws Exception {
        // A task that fails its readiness check (exit_code=1), should remain in the PENDING state on restart
        testTaskWithReadinessCheckHasStatus(1, Status.PENDING, false);
    }

    @Test
    public void completeTaskIsCompleteAfterRestart_CustomExecutor() throws Exception {
        // A task that succeeds its readiness check (exit_code=0), should remain in the COMPLETE state on restart
        testTaskWithReadinessCheckHasStatus(0, Status.COMPLETE, false);
    }

    private static void testTaskWithReadinessCheckHasStatus(
            int readinessCheckStatusCode, Status expectedStatus, boolean useDefaultExecutor) throws Exception {
        Collection<SimulationTick> ticks = new ArrayList<>();

        ticks.add(Send.register());

        ticks.add(Expect.reconciledImplicitly());

        ticks.add(Send.offerBuilder("hello").build());
        ticks.add(Expect.launchedTasks("hello-0-server"));

        ticks.add(Send.offerBuilder("world").build());
        ticks.add(Expect.declinedLastOffer());

        ticks.add(Send.taskStatus("hello-0-server", Protos.TaskState.TASK_RUNNING).build());

        ticks.add(Send.offerBuilder("world").build());
        ticks.add(Expect.launchedTasks("world-0-server"));

        ticks.add(Expect.stepStatus("deploy", "world", "world-0:[server]", Status.STARTING));

        ticks.add(Send.taskStatus("world-0-server", Protos.TaskState.TASK_RUNNING)
                .setReadinessCheckExitCode(readinessCheckStatusCode).build());

        Status postReadinessStatus;
        if (readinessCheckStatusCode == 0) {
            postReadinessStatus = Status.COMPLETE;
        } else {
            postReadinessStatus = Status.STARTED;
        }
        ticks.add(Expect.stepStatus("deploy", "world", "world-0:[server]", postReadinessStatus));

        ticks.add(Send.offerBuilder("world").build());
        ticks.add(Expect.declinedLastOffer());

        ServiceTestRunner runner;

        if (useDefaultExecutor) {
            runner = new ServiceTestRunner();
        } else {
            runner = new ServiceTestRunner().enableCustomExecutor();
        }
        ServiceTestResult result = runner.run(ticks);

        // Start a new scheduler:
        ticks.clear();

        ticks.add(Send.register());
        ticks.add(Expect.reconciledExplicitly(result.getPersister()));

        ticks.add(Expect.stepStatus("deploy", "world", "world-0:[server]", expectedStatus));

        new ServiceTestRunner().setState(result).run(ticks);
    }
}
