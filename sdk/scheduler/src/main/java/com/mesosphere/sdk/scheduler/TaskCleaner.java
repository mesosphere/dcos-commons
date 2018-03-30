package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.framework.TaskKiller;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * This class kills unexpected Tasks which are not in a terminal state.  This scenario could be encountered if a Task
 * was replaced while in a TASK_LOST or TASK_UNREACHABLE state, but then recovered from that non-terminal state.
 */
public class TaskCleaner {
    private final StateStore stateStore;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final boolean multithreaded;

    public TaskCleaner(StateStore stateStore, boolean multithreaded) {
        this.stateStore = stateStore;
        this.multithreaded = multithreaded;
    }

    public void statusUpdate(Protos.TaskStatus taskStatus) {
        if (multithreaded) {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    killUnexpectedTask(taskStatus);
                }
            });
        } else {
           killUnexpectedTask(taskStatus);
        }
    }

    private void killUnexpectedTask(Protos.TaskStatus taskStatus) {
        if (!shouldBeKilled(taskStatus)) {
            return;
        }

        Collection<Protos.TaskID> expectedTaskIds = stateStore.fetchTasks().stream()
                .map(taskInfo -> taskInfo.getTaskId())
                .collect(Collectors.toList());

        if (!expectedTaskIds.contains(taskStatus.getTaskId())) {
            TaskKiller.killTask(taskStatus.getTaskId());
        }
    }

    private boolean shouldBeKilled(Protos.TaskStatus taskStatus) {
        switch (taskStatus.getState()) {
            case TASK_RUNNING:
            case TASK_STAGING:
            case TASK_STARTING:
                return true;
            default:
                // Being extra careful here.  If you're not sure how to handle a status, err
                // on the side of not killing it.
                return false;
        }
    }
}
