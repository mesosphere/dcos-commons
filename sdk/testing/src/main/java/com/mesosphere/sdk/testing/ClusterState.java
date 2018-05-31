package com.mesosphere.sdk.testing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;

import com.google.protobuf.TextFormat;
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
    private final List<Collection<Protos.Offer>> sentOfferSets = new ArrayList<>();
    private final List<AcceptEntry> acceptCalls = new ArrayList<>();

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
        updatedClusterState.sentOfferSets.addAll(clusterState.sentOfferSets);
        updatedClusterState.acceptCalls.addAll(clusterState.acceptCalls);
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
     * Returns the scheduler's HTTP endpoint objects.
     */
    public Collection<Object> getHTTPEndpoints() {
        return scheduler.getHTTPEndpoints();
    }

    /**
     * Adds the provided offer to the list of sent offers.
     */
    public void addSentOffer(Protos.Offer offer) {
        sentOfferSets.add(Collections.singleton(offer));
    }

    /**
     * Adds all of the provided offers to the list of sent offers.
     */
    public void addSentOffers(Collection<Protos.Offer> offers) {
        sentOfferSets.add(offers);
    }

    /**
     * Returns the set of offers received in the last offer cycle.
     *
     * @return the last offer added via {@link #addSentOffer(org.apache.mesos.Protos.Offer)} or
     *          {@link #addSentOffers(Collection)}
     * @throws IllegalStateException if no offers had been sent
     */
    public Collection<Protos.Offer> getLastOfferCycle() {
        if (sentOfferSets.isEmpty()) {
            throw new IllegalStateException("No offers were sent yet");
        }
        return sentOfferSets.get(sentOfferSets.size() - 1);
    }

    /**
     * Adds the provided pod to the list of launched pods.
     */
    public void addAcceptCall(AcceptEntry pod) {
        if (pod.getTasks().isEmpty()) {
            throw new IllegalArgumentException("Refusing to record an empty pod");
        }
        acceptCalls.add(pod);
    }

    /**
     * Returns the last pod to be launched, regardless of the pod's name.
     *
     * @return the last pod added to {@link #addAcceptCall(Collection)}
     * @throws IllegalStateException if no pods had been launched
     * @see #getLastAcceptCall(String)
     */
    public AcceptEntry getLastAcceptCall() {
        if (acceptCalls.isEmpty()) {
            throw new IllegalStateException("No pods were created yet");
        }
        return acceptCalls.get(acceptCalls.size() - 1);
    }

    /**
     * Returns the last pod to be launched with the specified name.
     *
     * @param podName name+index of the pod, of the form "podtype-#"
     * @return a list of tasks which were included in the pod
     * @throws IllegalStateException if no such pod was found
     * @see #getLastAcceptCall()
     */
    public AcceptEntry getLastAcceptCall(String podName) {
        Set<String> allPodNames = new TreeSet<>();
        AcceptEntry foundAcceptCall = null;
        for (AcceptEntry acceptCall : acceptCalls) {
            // Check the tasks in this accept call for any tasks which match this pod:
            for (Protos.TaskInfo launchedTask : acceptCall.getTasks()) {
                final TaskLabelReader reader = new TaskLabelReader(launchedTask);
                final String thisPod;
                try {
                    thisPod = PodInstance.getName(reader.getType(), reader.getIndex());
                } catch (TaskException e) {
                    throw new IllegalStateException("Unable to extract pod from task " + launchedTask.getName(), e);
                }
                allPodNames.add(thisPod);
                if (thisPod.equals(podName)) {
                    foundAcceptCall = acceptCall;
                    // Stop checking tasks, but don't exit acceptCalls loop: want to collect the most recent version
                    break;
                }
            }
        }
        if (foundAcceptCall == null) {
            throw new IllegalStateException(String.format(
                    "Unable to find launched pod named %s. Available pods were: %s", podName, allPodNames));
        }
        return foundAcceptCall;
    }

    /**
     * Returns the last task launched with the specified name.
     *
     * @param taskName the task name to be found
     * @return the task's info
     * @throws IllegalStateException if no such task was found
     */
    public LaunchedTask getLastLaunchedTask(String taskName) {
        // Iterate over pods in sequential order, so that the newer version of a given task (by name) takes precedence
        // over an older version.
        // For example, given two versions of podX:
        // - podX v1: task[name=A, id=0] + task[name=B, id=1]
        // - podX v2: task[name=A, id=2] + task[name=C, id=3]
        // Resulting map:
        //   {A: 2, B: 1, C: 3}
        // Note: We COULD have just filtered against the task name up-front here, but it'd be more helpful to have a
        // mapping of all tasks available for the error message below.
        Map<String, LaunchedTask> tasksByName = new HashMap<>();
        for (AcceptEntry acceptCall : acceptCalls) {
            for (Protos.TaskInfo task : acceptCall.getTasks()) {
                // The accept call should also have an executor matching the launched task:
                tasksByName.put(
                        task.getName(), new LaunchedTask(findMatchingExecutor(task, acceptCall.getExecutors()), task));
            }
        }
        LaunchedTask task = tasksByName.get(taskName);
        if (task == null) {
            throw new IllegalStateException(String.format(
                    "Unable to find task named %s, known tasks were: %s", taskName, tasksByName.keySet()));
        }
        return task;
    }

    /**
     * Searches for exactly one executor in a given accept call which matches the task. The match is found by comparing
     * the executor name with the pod type of the task. In practice, there should only be one executor of a given type
     * in a given {@code acceptOffers()} call.
     */
    private static Protos.ExecutorInfo findMatchingExecutor(
            Protos.TaskInfo task, Collection<Protos.ExecutorInfo> executors) {
        final String podType;
        try {
            podType = new TaskLabelReader(task).getType();
        } catch (TaskException e) {
            throw new IllegalStateException("Unable to extract pod from task " + task.getName(), e);
        }
        Protos.ExecutorInfo matchingExecutor = null;
        for (Protos.ExecutorInfo executor : executors) {
            if (executor.getName().equals(podType)) {
                if (matchingExecutor != null) {
                    throw new IllegalStateException(String.format(
                            "Found multiple executors with pod type %s: %s",
                            podType, executors.stream()
                                    .map(e -> TextFormat.shortDebugString(e))
                                    .collect(Collectors.toList())));
                }
                matchingExecutor = executor;
            }
        }
        if (matchingExecutor == null) {
            throw new IllegalStateException(String.format(
                    "Expected at least one executor with pod type %s, got: %s",
                    podType, executors.stream().map(e -> e.getName()).collect(Collectors.toList())));
        }
        return matchingExecutor;
    }

    /**
     * Returns the last task id for a task of the specified name.
     *
     * @param taskName the task name to be found
     * @return the task id
     * @throws IllegalStateException if no such task was found
     */
    public Protos.TaskID getTaskId(String taskName) {
        return getLastLaunchedTask(taskName).getTask().getTaskId();
    }
}
