package com.mesosphere.sdk.scheduler.uninstall;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mesosphere.sdk.dcos.DcosHttpClientBuilder;
import com.mesosphere.sdk.dcos.DcosHttpExecutor;
import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.scheduler.TaskKiller;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.DefaultPhase;
import com.mesosphere.sdk.scheduler.plan.DefaultPlan;
import com.mesosphere.sdk.scheduler.plan.Phase;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.Status;
import com.mesosphere.sdk.scheduler.plan.Step;
import com.mesosphere.sdk.scheduler.plan.strategy.ParallelStrategy;
import com.mesosphere.sdk.scheduler.plan.strategy.SerialStrategy;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;

/**
 * Handles creation of the uninstall plan, returning information about the plan contents back to the caller.
 */
class UninstallPlanBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(UninstallPlanBuilder.class);

    private static final String TASK_KILL_PHASE = "kill-tasks";
    private static final String RESOURCE_PHASE = "unreserve-resources";
    private static final String TLS_CLEANUP_PHASE = "tls-cleanup";
    private static final String DEREGISTER_PHASE = "deregister-service";

    private final Plan plan;

    UninstallPlanBuilder(
            ServiceSpec serviceSpec,
            StateStore stateStore,
            ConfigStore<ServiceSpec> configStore,
            SchedulerConfig schedulerConfig,
            SchedulerDriver driver,
            Optional<SecretsClient> customSecretsClientForTests) {

        // If there is no framework ID, wipe ZK and produce an empty COMPLETE plan
        if (!stateStore.fetchFrameworkId().isPresent()) {
            LOGGER.info("Framework ID is unset. Clearing state data and using an empty completed plan.");
            stateStore.clearAllData();
            plan = new DefaultPlan(Constants.DEPLOY_PLAN_NAME, Collections.emptyList());
            return;
        }

        List<Phase> phases = new ArrayList<>();

        // First, we kill all the tasks, so that we may release their reserved resources.
        TaskKiller taskKiller = new TaskKiller(driver);
        List<Step> taskKillSteps = stateStore.fetchTasks().stream()
                .map(Protos.TaskInfo::getTaskId)
                .map(taskID -> new TaskKillStep(taskID, taskKiller))
                .collect(Collectors.toList());
        phases.add(new DefaultPhase(TASK_KILL_PHASE, taskKillSteps, new ParallelStrategy<>(), Collections.emptyList()));

        // Next, we unreserve the resources for the tasks as they're offered to us.
        // Given this scenario:
        // - Task 1: resource A, resource B
        // - Task 2: resource A, resource C
        // Create one UninstallStep per unique Resource, including Executor resources.
        // We filter to unique Resource Id's, because Executor level resources are tracked on multiple Tasks.
        // So in this scenario we should have 3 uninstall steps around resources A, B, and C.

        // Filter the tasks to those that have actually created resources. Tasks in an ERROR state which are also
        // flagged as permanently failed are assumed to not have resources reserved on Mesos' end, despite our State
        // Store still listing them with resources. This is because we log the planned reservation before it occurs.
        Collection<Protos.TaskInfo> allTasks = stateStore.fetchTasks();
        List<Protos.TaskID> taskIdsInErrorState = stateStore.fetchStatuses().stream()
                .filter(taskStatus -> taskStatus.getState() == Protos.TaskState.TASK_ERROR)
                .map(Protos.TaskStatus::getTaskId)
                .collect(Collectors.toList());
        List<Protos.TaskInfo> tasksNotFailedAndErrored = allTasks.stream()
                .filter(taskInfo -> !(FailureUtils.isPermanentlyFailed(taskInfo)
                        && taskIdsInErrorState.contains(taskInfo.getTaskId())))
                .collect(Collectors.toList());

        List<Step> resourceSteps =
                ResourceUtils.getResourceIds(ResourceUtils.getAllResources(tasksNotFailedAndErrored)).stream()
                        .map(resourceId -> new ResourceCleanupStep(
                                resourceId,
                                resourceId.startsWith(Constants.TOMBSTONE_MARKER) ? Status.COMPLETE : Status.PENDING))
                        .collect(Collectors.toList());
        LOGGER.info("Configuring resource cleanup of {}/{} tasks: {}/{} expected resources have been unreserved",
                tasksNotFailedAndErrored.size(), allTasks.size(),
                resourceSteps.stream().filter(step -> step.isComplete()).count(),
                resourceSteps.size());
        phases.add(new DefaultPhase(RESOURCE_PHASE, resourceSteps, new ParallelStrategy<>(), Collections.emptyList()));

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
                                secretsClient, schedulerConfig.getSecretsNamespace(serviceSpec.getName()))),
                        new SerialStrategy<>(),
                        Collections.emptyList()));
            } catch (Exception e) {
                LOGGER.error("Failed to create a secrets store client, " +
                        "TLS artifacts possibly won't be cleaned up from secrets store", e);
            }
        }

        // Finally, we unregister the framework from Mesos.
        // We don't have access to the SchedulerDriver yet. That will be set via setSchedulerDriver() below.
        phases.add(new DefaultPhase(
                DEREGISTER_PHASE,
                Collections.singletonList(new DeregisterStep(stateStore, driver)),
                new SerialStrategy<>(),
                Collections.emptyList()));

        plan = new DefaultPlan(Constants.DEPLOY_PLAN_NAME, phases);
    }

    /**
     * Returns the plan to be used for uninstalling the service.
     */
    Plan build() {
        return plan;
    }
}
