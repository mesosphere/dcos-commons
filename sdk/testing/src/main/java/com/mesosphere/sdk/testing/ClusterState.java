package com.mesosphere.sdk.testing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.mesos.Protos;

import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.plan.Plan;

/**
 * Representation of the state of a cluster. Effectively a log of prior offers and task launches during a test run.
 */
public class ClusterState {

    private final SchedulerConfigResult configResult;
    private final AbstractScheduler scheduler;
    private final List<Protos.Offer> sentOffers = new ArrayList<>();
    private final List<Collection<Protos.TaskInfo>> createdPods = new ArrayList<>();

    public ClusterState(SchedulerConfigResult configResult, AbstractScheduler scheduler) {
        this.configResult = configResult;
        this.scheduler = scheduler;
    }

    /**
     * Returns the rendered scheduler/service configuration.
     */
    public SchedulerConfigResult getSchedulerConfig() {
        return configResult;
    }

    /**
     * Returns the scheduler's current plans.
     */
    public Collection<Plan> getPlans() {
        return scheduler.getPlans();
    }

    /**
     * Adds the provided offer to the list of sent offers.
     */
    public void addSentOffer(Protos.Offer offer) {
        sentOffers.add(offer);
    }

    /**
     * Returns the last offer to have been sent.
     *
     * @return the last offer added to {@link #addSentOffer(org.apache.mesos.Protos.Offer)}
     * @throws IllegalStateException if no pods had been sent
     */
    public Protos.Offer getLastOffer() {
        if (sentOffers.isEmpty()) {
            throw new IllegalStateException("No offers were sent yet");
        }
        return sentOffers.get(sentOffers.size() - 1);
    }

    /**
     * Adds the provided pod to the list of launched pods.
     */
    public void addLaunchedPod(Collection<Protos.TaskInfo> pod) {
        if (pod.isEmpty()) {
            throw new IllegalArgumentException("Refusing to record an empty pod");
        }
        createdPods.add(pod);
    }

    /**
     * Returns the last pod to be launched.
     *
     * @return the last pod added to {@link #addLaunchedPod(Collection)}
     * @throws IllegalStateException if no pods had been launched
     */
    public Collection<Protos.TaskInfo> getLastLaunchedPod() {
        if (createdPods.isEmpty()) {
            throw new IllegalStateException("No pods were created yet");
        }
        return createdPods.get(createdPods.size() - 1);
    }

    /**
     * Returns the last pod to be launched with the specified name.
     *
     * @param podName name+index of the pod, of the form "podtype-#"
     * @return a list of tasks which were included in the pod
     * @throws IllegalStateException if no such pod was found
     */
    public Collection<Protos.TaskInfo> getPod(String podName) {
        Set<String> allPodNames = new TreeSet<>();
        Collection<Protos.TaskInfo> foundPod = null;
        for (Collection<Protos.TaskInfo> pod : createdPods) {
            final Protos.TaskInfo task = pod.iterator().next(); // sample from the first task. should all be the same.
            final TaskLabelReader reader = new TaskLabelReader(task);
            final String thisPod;
            try {
                thisPod = String.format("%s-%d", reader.getType(), reader.getIndex());
            } catch (TaskException e) {
                throw new IllegalStateException("Unable to extract pod from task " + task.getName(), e);
            }
            allPodNames.add(thisPod);
            if (thisPod.equals(podName)) {
                foundPod = pod;
                // Don't break: want to collect the most recent version
            }
        }
        if (foundPod == null) {
            throw new IllegalStateException(String.format(
                    "Unable to find pod named %s. Available pods were: %s", podName, allPodNames));
        }
        return foundPod;
    }

    /**
     * Returns the last task id for a task of the specified name
     *
     * @param taskName the task name to be found
     * @return the task id
     * @throws IllegalStateException if no such task was found
     */
    public Protos.TaskID getTaskId(String taskName) {
        Map<String, Protos.TaskID> taskIdsByName = new HashMap<>();
        // Collect in sequential order: Newer tasks override older tasks.
        for (Collection<Protos.TaskInfo> pod : createdPods) {
            for (Protos.TaskInfo task : pod) {
                taskIdsByName.put(task.getName(), task.getTaskId());
            }
        }
        Protos.TaskID taskId = taskIdsByName.get(taskName);
        if (taskId == null) {
            throw new IllegalStateException(String.format(
                    "Unable to find task named %s, known tasks were: %s", taskName, taskIdsByName));
        }
        return taskId;
    }
}
