package org.apache.mesos.scheduler.registry;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;
import org.apache.mesos.offer.TaskRequirement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A task is a monotonic object, which contains the TaskInfo, TaskStatus, name,
 * and how to launch it. Everything except for the status is immutable; the
 * status is actually monotonic, in that we always append new statuses, so that
 * it's possible to review earlier statuses as well.
 */
public class Task {
    private final Protos.TaskInfo taskInfo;
    //all accesses to taskStatuses should be synchronized on it
    private final List<Protos.TaskStatus> taskStatuses;
    private final OfferRequirement requirement;
    private final String name;

    protected static Task createTask(String name, OfferRequirement requirement) {
        if (requirement.getTaskRequirements().size() != 1) {
            throw new RuntimeException("Must be exactly one task requirement!");
        }
        TaskRequirement taskReq = requirement.getTaskRequirements().iterator().next();
        Protos.TaskInfo info = requirement.getTaskRequirements().iterator().next().getTaskInfo();
        return new Task(name, info, requirement);
    }

    private Task(String name, Protos.TaskInfo info, OfferRequirement requirement) {
        this.name = name;
        this.requirement = requirement;
        this.taskInfo = info;
        this.taskStatuses = new ArrayList<>();
    }

    public Protos.TaskInfo getTaskInfo() {
        return taskInfo;
    }

    public void launch() {
        synchronized (taskStatuses) {
            taskStatuses.add(Protos.TaskStatus.newBuilder()
                    .setTaskId(taskInfo.getTaskId())
                    .setState(Protos.TaskState.TASK_STAGING)
                    .build());
        }
    }

    public void updateStatus(Protos.TaskStatus newStatus) {
        synchronized (taskStatuses) {
            taskStatuses.add(newStatus);
        }
    }

    public boolean hasStatus() {
        synchronized (taskStatuses) {
            return taskStatuses.isEmpty();
        }
    }

    public List<Protos.TaskStatus> getTaskStatuses() {
        synchronized (taskStatuses) {
            return new ArrayList<>(taskStatuses);
        }
    }

    public OfferRequirement getRequirement() {
        return requirement;
    }

    public String getName() {
        return name;
    }

    public Protos.TaskStatus getLatestTaskStatus() {
        synchronized (taskStatuses) {
            if (!hasStatus()) {
                return null;
            } else {
                return taskStatuses.get(taskStatuses.size() - 1);
            }
        }
    }

    //TODO should this have a timeout version?
    public void waitForStatus(Predicate<Protos.TaskStatus> pred)
            throws InterruptedException {
        while (!pred.test(getLatestTaskStatus())) {
            this.wait();
        }
    }
}
