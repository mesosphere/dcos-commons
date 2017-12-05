package com.mesosphere.sdk.scheduler.recovery.monitor;

import org.apache.mesos.Protos.TaskInfo;

import java.util.Arrays;
import java.util.List;

/**
 * Simple API to enable testing of failure conditions in frameworks.
 * <p>
 * Allows you to specify which {@link TaskInfo}s will be treated as having failed. Any non-specified {@link TaskInfo}s
 * will be treated as stopped.
 */
public class TestingFailureMonitor implements FailureMonitor {
    private List<TaskInfo> failedList;

    public TestingFailureMonitor(TaskInfo... failed) {
        this.failedList = Arrays.asList(failed);
    }

    public void setFailedList(TaskInfo... failed) {
        this.failedList = Arrays.asList(failed);
    }

    @Override
    public boolean hasFailed(TaskInfo task) {
        return failedList.stream().anyMatch(task::equals);
    }
}
