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
import com.mesosphere.sdk.specification.PodInstance;
import com.mesosphere.sdk.specification.ServiceSpec;

/**
 * Representation of the state of a cluster. Effectively a log of prior offers and task launches during a test run.
 */
public class ClusterState {

    private final ServiceSpec serviceSpec;
    private final AbstractScheduler scheduler;
    private final List<Protos.Offer> sentOffers = new ArrayList<>();
    private final List<Collection<Protos.TaskInfo>> createdPods = new ArrayList<>();

    private ClusterState(ServiceSpec serviceSpec, AbstractScheduler scheduler) {
        this.serviceSpec = serviceSpec;
        this.scheduler = scheduler;
    }

    public static ClusterState create(ServiceSpec serviceSpec, AbstractScheduler scheduler) {
        return new ClusterState(serviceSpec, scheduler);
    }

    public static ClusterState withUpdatedConfig(
            ClusterState clusterState, ServiceSpec serviceSpec, AbstractScheduler scheduler) {
        ClusterState updatedClusterState = create(serviceSpec, scheduler);
        updatedClusterState.sentOffers.addAll(clusterState.sentOffers);
        updatedClusterState.createdPods.addAll(clusterState.createdPods);
        return updatedClusterState;
    }

    /**
     * Returns the rendered scheduler/service configuration.
     */
    public ServiceSpec getServiceSpec() {
        return serviceSpec;
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
     * Returns the last pod to be launched, regardless of the pod's name.
     *
     * @return the last pod added to {@link #addLaunchedPod(Collection)}
     * @throws IllegalStateException if no pods had been launched
     * @see #getLastLaunchedPod(String)
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
     * @see #getLastLaunchedPod()
     */
    public Collection<Protos.TaskInfo> getLastLaunchedPod(String podName) {
        Set<String> allPodNames = new TreeSet<>();
        Collection<Protos.TaskInfo> foundPod = null;
        for (Collection<Protos.TaskInfo> pod : createdPods) {
            final Protos.TaskInfo task = pod.iterator().next(); // sample from the first task. should all be the same.
            final TaskLabelReader reader = new TaskLabelReader(task);
            final String thisPod;
            try {
                thisPod = PodInstance.getName(reader.getType(), reader.getIndex());
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
     * Returns the last task launched with the specified name.
     *
     * @param taskName the task name to be found
     * @return the task's info
     * @throws IllegalStateException if no such task was found
     */
    public Protos.TaskInfo getLastLaunchedTask(String taskName) {
        // Iterate over pods in sequential order, so that the newer version of a given task (by name) takes precedence
        // over an older version.
        // For example, given two versions of podX:
        // - podX v1: task[name=A, id=0] + task[name=B, id=1]
        // - podX v2: task[name=A, id=2] + task[name=C, id=3]
        // Resulting map:
        //   {A: 2, B: 1, C: 3}
        // Note: We COULD have just filtered against the task name up-front here, but it'd be more helpful to have a
        // mapping of all tasks available for the error message below.
        Map<String, Protos.TaskInfo> taskInfosByName = new HashMap<>();
        for (Collection<Protos.TaskInfo> pod : createdPods) {
            for (Protos.TaskInfo task : pod) {
                taskInfosByName.put(task.getName(), task);
            }
        }
        Protos.TaskInfo taskInfo = taskInfosByName.get(taskName);
        if (taskInfo == null) {
            throw new IllegalStateException(String.format(
                    "Unable to find task named %s, known tasks were: %s", taskName, taskInfosByName.keySet()));
        }
        return taskInfo;
    }

    /**
     * Returns the last task id for a task of the specified name.
     *
     * @param taskName the task name to be found
     * @return the task id
     * @throws IllegalStateException if no such task was found
     */
    public Protos.TaskID getTaskId(String taskName) {
        return getLastLaunchedTask(taskName).getTaskId();
    }
}
