package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.dcos.DcosHttpClientBuilder;
import com.mesosphere.sdk.dcos.DcosHttpExecutor;
import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.DefaultPhase;
import com.mesosphere.sdk.scheduler.plan.DefaultPlan;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.strategy.DependencyStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.DependencyStrategyHelper;
import com.mesosphere.sdk.scheduler.plan.strategy.ParallelStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.StateStore;

import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Handles creation of the uninstall plan, returning information about the plan contents back to the caller.
 */
public class UninstallPlanFactory {
  private static final Logger LOGGER = LoggingUtils.getLogger(UninstallPlanFactory.class);

  private static final String TASK_KILL_PHASE = "kill-tasks";

  private static final String RESOURCE_PHASE_PREFIX = "unreserve-resources-";

  private static final String TLS_CLEANUP_PHASE = "tls-cleanup";

  private static final String DEREGISTER_PHASE = "deregister-service";

  private final Plan plan;

  private final Collection<ResourceCleanupStep> allResourceCleanupSteps;

  private final DeregisterStep deregisterStep;

  UninstallPlanFactory(
      ServiceSpec serviceSpec,
      StateStore stateStore,
      SchedulerConfig schedulerConfig,
      Optional<SecretsClient> customSecretsClientForTests)
  {
    List<Phase> phases = new ArrayList<>();

    // First, we kill all the tasks, so that we may release their reserved resources.
    List<Step> taskKillSteps = stateStore.fetchTasks().stream()
        .map(Protos.TaskInfo::getTaskId)
        .map(TaskKillStep::new)
        .collect(Collectors.toList());
    phases.add(new DefaultPhase(
        TASK_KILL_PHASE,
        taskKillSteps,
        new ParallelStrategy<>(),
        Collections.emptyList()
    ));

    // Next, we unreserve the resources for the tasks as they're offered to us.
    // Given this scenario:
    // - Task 1: resource A, resource B
    // - Task 2: resource A, resource C
    // Create one UninstallStep per unique Resource, including Executor resources.
    // We filter to unique Resource Id's, because Executor level resources are tracked on multiple Tasks.
    // So in this scenario we should have 3 uninstall steps around resources A, B, and C.

    this.allResourceCleanupSteps = new ArrayList<>();

    // Map entries should be sorted alphabetically by agent hostname.
    // This sorting does not affect uninstall functionality and is just a nice-to-have for users.
    for (Map.Entry<String, Set<String>> entry : getResourceIdsByAgentHost(stateStore).entrySet()) {
      // Resource IDs should be sorted alhpabetically:
      List<ResourceCleanupStep> agentSteps = entry.getValue().stream()
          .map(ResourceCleanupStep::new)
          .collect(Collectors.toList());

      this.allResourceCleanupSteps.addAll(agentSteps);

      phases.add(new DefaultPhase(
          RESOURCE_PHASE_PREFIX + entry.getKey(),
          // Generic type alignment: List<RCS> => List<Step>
          new ArrayList<>(agentSteps),
          new ParallelStrategy<>(),
          Collections.emptyList()));
    }

    LOGGER.info("{}/{} resources remain to be unreserved",
        allResourceCleanupSteps.stream().filter(step -> !step.isComplete()).count(),
        allResourceCleanupSteps.size());

    // If applicable, we also clean up any TLS secrets that we'd created before.
    // Note: This won't catch certificates where the user installed the service with TLS enabled, then disabled TLS
    // before uninstalling the service. Ideally, at uninstall time (and no sooner, to avoid deleting certs that were
    // only disabled temporarily) we would detect that TLS was *ever* enabled, rather than just *currently* enabled.
    // See also INFINITY-2464.
    if (TaskUtils.hasTasksWithTLS(serviceSpec)) {
      try {
        // Use any provided custom test client, or otherwise construct a default client
        SecretsClient secretsClient = customSecretsClientForTests.isPresent()
            ? customSecretsClientForTests.get()
            : new SecretsClient(
            new DcosHttpExecutor(new DcosHttpClientBuilder()
                .setTokenProvider(schedulerConfig.getDcosAuthTokenProvider())
                .setRedirectStrategy(new LaxRedirectStrategy())));
        phases.add(new DefaultPhase(
            TLS_CLEANUP_PHASE,
            Collections.singletonList(new TLSCleanupStep(
                secretsClient,
                schedulerConfig.getSecretsNamespace(serviceSpec.getName()))),
            new SerialStrategy<>(),
            Collections.emptyList()));
      } catch (Exception e) { // SUPPRESS CHECKSTYLE IllegalCatch
        LOGGER.error("Failed to create a secrets store client, " +
            "TLS artifacts possibly won't be cleaned up from secrets store", e);
      }
    }

    // Finally, we wipe remaining ZK data and unregister the framework from Mesos.
    // This is done upstream in FrameworkRunner, then the step is notified when it completes.
    this.deregisterStep = new DeregisterStep();
    Phase deregisterPhase = new DefaultPhase(
        DEREGISTER_PHASE,
        Collections.singletonList(deregisterStep),
        new SerialStrategy<>(),
        Collections.emptyList());

    // We need to construct a custom strategy in order to allow the resource phases to operate in parallel.
    // In other words, the dependencies should look like this:
    // 1. Kill all tasks, unreserve all resources, and delete any automatically created TLS certs as needed
    // 2. When all of the above is complete, deregister
    DependencyStrategyHelper<Phase> uninstallPhaseDependencies =
        new DependencyStrategyHelper<>(phases);
    // Ensure the deregister phase is also added as an element before we start setting up dependencies:
    uninstallPhaseDependencies.addElement(deregisterPhase);

    // Mark all OTHER phases as dependencies of deregisterPhase:
    for (Phase phase : phases) {
      uninstallPhaseDependencies.addDependency(deregisterPhase, phase);
    }

    // AFTER we've configured all the dependencies, add the deregister phase to the full list of phases:
    phases.add(deregisterPhase);

    plan = new DefaultPlan(
        Constants.DEPLOY_PLAN_NAME,
        phases,
        new DependencyStrategy<>(uninstallPhaseDependencies));
  }

  /**
   * Returns a grouped mapping of agent hostname to resource ids present on that agent.
   * <p>
   * The resulting map will be sorted alphabetically by agent hostname, and the resource ids within each agent entry
   * will also be sorted alphabetically.
   */
  private static Map<String, Set<String>> getResourceIdsByAgentHost(StateStore stateStore) {
    Collection<Protos.TaskInfo> allTasks = stateStore.fetchTasks();
    Set<String> taskIdsInErrorState = stateStore.fetchStatuses().stream()
        .filter(taskStatus -> taskStatus.getState().equals(Protos.TaskState.TASK_ERROR))
        .map(taskStatus -> taskStatus.getTaskId().getValue())
        .collect(Collectors.toSet());

    // Filter the tasks to those that have actually created resources. Tasks in an ERROR state which are also
    // flagged as permanently failed are assumed to not have resources reserved on Mesos' end, despite our State
    // Store still listing them with resources. This is because we log the planned reservation before it occurs.
    Collection<Protos.TaskInfo> tasksWithExpectedReservations = allTasks.stream()
        .filter(taskInfo -> !(FailureUtils.isPermanentlyFailed(taskInfo)
            && taskIdsInErrorState.contains(taskInfo.getTaskId().getValue())))
        .collect(Collectors.toList());

    // The agent hostname mapping is sorted alphabetically. This doesn't affect functionality and is just for user
    // experience when viewing the uninstall plan.
    Map<String, Set<String>> resourceIdsByAgentHost = new TreeMap<>();
    for (Protos.TaskInfo taskInfo : tasksWithExpectedReservations) {
      String hostname;
      try {
        hostname = new TaskLabelReader(taskInfo).getHostname();
      } catch (TaskException e) {
        LOGGER.warn(
            String.format("Failed to determine hostname of task %s", taskInfo.getName()),
            e
        );
        hostname = "UNKNOWN_AGENT";
      }

      // Sort the resource ids alphabetically within each agent. This doesn't affect functionality and is just
      // for user experience when viewing the uninstall plan.
      resourceIdsByAgentHost
          .computeIfAbsent(hostname, k -> new TreeSet<>())
          .addAll(ResourceUtils.getResourceIds(ResourceUtils.getAllResources(taskInfo)));
    }

    LOGGER.info("Configuring resource cleanup of {}/{} tasks across {} agents",
        tasksWithExpectedReservations.size(), allTasks.size(), resourceIdsByAgentHost.size());
    return resourceIdsByAgentHost;
  }

  /**
   * Returns the plan to be used for uninstalling the service.
   */
  Plan getPlan() {
    return plan;
  }

  /**
   * Returns the resource cleanup steps.
   */
  Collection<ResourceCleanupStep> getResourceCleanupSteps() {
    return allResourceCleanupSteps;
  }

  /**
   * Returns the deregister step.
   */
  DeregisterStep getDeregisterStep() {
    return deregisterStep;
  }
}
