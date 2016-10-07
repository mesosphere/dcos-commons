package org.apache.mesos.scheduler.recovery.monitor;

import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.scheduler.recovery.FailureUtils;

import java.util.ArrayList;
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
        failedList = new ArrayList<>();
        for (TaskInfo info : failed) {
            failedList.add(info);
        }
    }

    public void setFailedList(TaskInfo... failed) {
        failedList = new ArrayList<>();
        for (TaskInfo info : failed) {
            failedList.add(info);
            FailureUtils.markFailed(info);
        }
    }

    @Override
    public boolean hasFailed(TaskInfo task) {
        System.out.println("looking at " + task);
        System.out.println("have " + failedList);
        return failedList.stream().anyMatch(task::equals);
    }
}
