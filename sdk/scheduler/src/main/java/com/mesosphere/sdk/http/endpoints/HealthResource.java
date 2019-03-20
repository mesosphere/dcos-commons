package com.mesosphere.sdk.http.endpoints;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.StateStoreException;

import com.google.common.annotations.VisibleForTesting;
import org.apache.mesos.Protos;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// CHECKSTYLE:OFF LineLengthCheck

/**
 * This class returns a single code representing the status of the service
 * with the following priority.
 * HTTP Response Code, Priority, Reason
 * 418, 1, Initializing
 * 200, 1, Running
 * 500, 1, Error Creating Service
 * 204, 2, Deploying:Pending
 * 202, 2, Deploying:Starting
 * 206, 3, Degraded
 * 203, 4, Recovering:Pending
 * 205, 4, Recovering:Starting
 * 420, 5, Backing Up
 * 421, 5, Restoring
 * 426, 6, Upgrade/Rollback/Downgrade
 * 503,  , Service Unavailable (Priority Undefined)
 */
@Path("/v1/health")
public class HealthResource {

  /**
   * ServiceStatusCode encodes the service status code.
   */
  @VisibleForTesting
  protected enum ServiceStatusCode {

    INITIALIZING(418, 1),
    RUNNING(200, 1),
    ERROR_CREATING_SERVICE(500, 1),
    DEPLOYING_PENDING(204, 2),
    DEPLOYING_STARTING(202, 2),
    DEGRADED(206, 3),
    RECOVERING_PENDING(203, 4),
    RECOVERING_STARTING(205, 4),
    BACKING_UP(420, 5),
    RESTORING(421, 5),
    UPGRADE_ROLLBACK_DOWNGRADE(426, 6),
    SERVICE_UNAVAILABLE(503, -1);

    private final int statusCode;
    private final int priority;

    ServiceStatusCode(int statusCode, int priority) {
      this.statusCode = statusCode;
      this.priority = priority;
    }
  }

  private static final String RESULT_CODE_KEY = "value";

  private static final String BACKUP_PLAN_REGEXP = ".*back.*";

  private static final String RESTORE_PLAN_REGEXP = ".*restor.*";

  private final PlanCoordinator planCoordinator;

  private final Optional<FrameworkStore> frameworkStore;

  public HealthResource(PlanCoordinator planCoordinator, Optional<FrameworkStore> frameworkStore) {
    this.planCoordinator = planCoordinator;
    this.frameworkStore = frameworkStore;
  }

  /**
   * Returns the health of the service as a response code.
   */
  @GET
  public Response getHealth(@QueryParam("verbose") boolean isVerbose) {
    ServiceStatusResult serviceStatusResult = evaluateServiceStatus(isVerbose);
    return serviceStatusResult.getServiceStatusResponse();
  }

  @VisibleForTesting
  protected ServiceStatusResult evaluateServiceStatus(boolean isVerbose) {

    // Final response object we're going to return.
    JSONObject response = new JSONObject();

    Optional<ServiceStatusCode> serviceStatusCode;
    JSONArray statusCodeReasons = new JSONArray();

    ServiceStatusEvaluationStage initializing = isServiceInitializing();
    ServiceStatusEvaluationStage isErrorCreating = isErrorCreatingService();
    ServiceStatusEvaluationStage deploymentComplete = isDeploymentComplete(initializing.getServiceStatusCode());
    ServiceStatusEvaluationStage isDeploying = isDeploying();
    ServiceStatusEvaluationStage isDegraded = notImplemented(ServiceStatusCode.DEGRADED);
    ServiceStatusEvaluationStage isRecovering = isRecovering();
    ServiceStatusEvaluationStage isBackingUp = isBackingUp();
    ServiceStatusEvaluationStage isRestoring = isRestoring();
    ServiceStatusEvaluationStage isUpgradeRollbackDowngrade = notImplemented(ServiceStatusCode.UPGRADE_ROLLBACK_DOWNGRADE);

    if (isVerbose) {
      statusCodeReasons.put(isErrorCreating.getStatusReason());
      statusCodeReasons.put(initializing.getStatusReason());
      statusCodeReasons.put(deploymentComplete.getStatusReason());
      statusCodeReasons.put(isDeploying.getStatusReason());
      statusCodeReasons.put(isDegraded.getStatusReason());
      statusCodeReasons.put(isRecovering.getStatusReason());
      statusCodeReasons.put(isBackingUp.getStatusReason());
      statusCodeReasons.put(isRestoring.getStatusReason());
      statusCodeReasons.put(isUpgradeRollbackDowngrade.getStatusReason());
      response.put("reasons", statusCodeReasons);
    }

    if (isErrorCreating.getServiceStatusCode().isPresent()) {
      serviceStatusCode = isErrorCreating.getServiceStatusCode();
    } else if (initializing.getServiceStatusCode().isPresent()) {
      serviceStatusCode = initializing.getServiceStatusCode();
    } else if (isDeploying.getServiceStatusCode().isPresent()) {
      serviceStatusCode = isDeploying.getServiceStatusCode();
    } else if (isDegraded.getServiceStatusCode().isPresent()) {
      serviceStatusCode = isDegraded.getServiceStatusCode();
    } else if (isRecovering.getServiceStatusCode().isPresent()) {
      serviceStatusCode = isRecovering.getServiceStatusCode();
    } else {
      serviceStatusCode = deploymentComplete.getServiceStatusCode();
    }

    // These are low-priority codes that require a deployment to be complete.
    if (serviceStatusCode.equals(Optional.of(ServiceStatusCode.RUNNING))) {
      if (isRestoring.getServiceStatusCode().isPresent()) {
        serviceStatusCode = isRestoring.getServiceStatusCode();
      } else if (isBackingUp.getServiceStatusCode().isPresent()) {
        serviceStatusCode = isBackingUp.getServiceStatusCode();
      } else if (isUpgradeRollbackDowngrade.getServiceStatusCode().isPresent()) {
        serviceStatusCode = isUpgradeRollbackDowngrade.getServiceStatusCode();
      }
    }

    // Service unavailable if nothing else.
    if (!serviceStatusCode.isPresent()) {
      serviceStatusCode = Optional.of(ServiceStatusCode.SERVICE_UNAVAILABLE);
    }

    response.put(RESULT_CODE_KEY, serviceStatusCode.get().statusCode);

    return new ServiceStatusResult(serviceStatusCode,
        ResponseUtils.jsonResponseBean(response, serviceStatusCode.get().statusCode));
  }

  private ServiceStatusEvaluationStage isRestoring() {
    String reason;
    Optional<ServiceStatusCode> statusCode;
    ServiceStatusCode restoring = ServiceStatusCode.RESTORING;

    Set<Plan> restorePlans = planCoordinator.getPlanManagers()
        .stream()
        .filter(planManager -> planManager.getPlan().getName().matches(RESTORE_PLAN_REGEXP))
        .map(planManager -> planManager.getPlan())
        .collect(Collectors.toSet());

    if (restorePlans.isEmpty()) {
      reason = String.format("Priority %d. Status Code %s is FALSE. No restore plans detected.",
          restoring.priority,
          restoring.statusCode);
      statusCode = Optional.empty();
    } else {
      // Found plan name with "restore" in it, check if any are running, if so get their names.
      Set<String> runningRestorePlans = restorePlans.stream()
          .filter(plan -> plan.isRunning())
          .map(plan -> plan.getName())
          .collect(Collectors.toSet());

      if (runningRestorePlans.isEmpty()) {
        Set<String> notRunningRestorePlans = restorePlans.stream()
            .map(plan -> plan.getName())
            .collect(Collectors.toSet());

        reason = String.format("Priority %d. Status Code %s is FALSE. Following restore plans not running: %s",
            restoring.priority,
            restoring.statusCode,
            String.join(", ", notRunningRestorePlans));
        statusCode = Optional.empty();
      } else {
        // Found running restore plans.
        reason = String.format("Priority %d. Status Code %s is TRUE. Following restore plans found running: %s",
            restoring.priority,
            restoring.statusCode,
            String.join(", ", runningRestorePlans));
        statusCode = Optional.of(restoring);
      }
    }
    return new ServiceStatusEvaluationStage(statusCode, reason);
  }

  private ServiceStatusEvaluationStage isBackingUp() {
    String reason;
    Optional<ServiceStatusCode> statusCode;

    ServiceStatusCode backingUp = ServiceStatusCode.BACKING_UP;
    Set<Plan> backupPlans = planCoordinator.getPlanManagers()
        .stream()
        .filter(planManager -> planManager.getPlan().getName().matches(BACKUP_PLAN_REGEXP))
        .map(planManager -> planManager.getPlan())
        .collect(Collectors.toSet());

    if (backupPlans.isEmpty()) {
      reason = String.format("Priority %d. Status Code %s is FALSE. No backup plans detected.",
          backingUp.priority,
          backingUp.statusCode);
      statusCode = Optional.empty();
    } else {
      // Found plan name with "backup" in it, check if any are running, if so get their names.
      Set<String> runningBackupPlans = backupPlans.stream()
          .filter(plan -> plan.isRunning())
          .map(plan -> plan.getName())
          .collect(Collectors.toSet());

      if (runningBackupPlans.isEmpty()) {
        Set<String> notRunningBackupPlans = backupPlans.stream()
            .map(plan -> plan.getName())
            .collect(Collectors.toSet());

        reason = String.format("Priority %d. Status Code %s is FALSE. Following backup plans not running: %s",
            backingUp.priority,
            backingUp.statusCode,
            String.join(", ", notRunningBackupPlans));
        statusCode = Optional.empty();
      } else {
        // Found running backup plans.
        reason = String.format("Priority %d. Status Code %s is TRUE. Following backup plans found running: %s",
            backingUp.priority,
            backingUp.statusCode,
            String.join(", ", runningBackupPlans));
        statusCode = Optional.of(backingUp);
      }
    }
    return new ServiceStatusEvaluationStage(statusCode, reason);
  }

  private ServiceStatusEvaluationStage isRecovering() {

    String reason;
    Optional<ServiceStatusCode> statusCode;

    // Get the recovery plan.
    Plan recoveryPlan = planCoordinator.getPlanManagers()
            .stream()
            .filter(planManager -> planManager.getPlan().isRecoveryPlan())
            .findFirst()
            .get()
            .getPlan();

    if (recoveryPlan.isComplete()) {
      reason = String.format("Priority %d. Status Code %s and %s is FALSE. Recovery plan is complete.",
          ServiceStatusCode.RECOVERING_PENDING.priority,
          ServiceStatusCode.RECOVERING_PENDING.statusCode,
          ServiceStatusCode.RECOVERING_STARTING.statusCode);
      statusCode = Optional.empty();

      return new ServiceStatusEvaluationStage(statusCode, reason);
    } else {
      // Recovery plan is NOT complete.
      return evaluatePendingOrStartingStatusCode(recoveryPlan,
          ServiceStatusCode.RECOVERING_PENDING,
          ServiceStatusCode.RECOVERING_STARTING,
          ServiceStatusCode.RECOVERING_PENDING.priority);
    }
  }

  private ServiceStatusEvaluationStage notImplemented(ServiceStatusCode statusCode) {
    String reason = String.format("Priority %d. Status Code %s is FALSE, Not implemented yet.",
        statusCode.priority,
        statusCode.statusCode);
    return new ServiceStatusEvaluationStage(Optional.empty(), reason);
  }

  private ServiceStatusEvaluationStage isDeploying() {

    final int priority = 2;

    // Get the deployment plan.
    Plan deploymentPlan = planCoordinator.getPlanManagers()
            .stream()
            .filter(planManager -> planManager.getPlan().isDeployPlan())
            .findFirst()
            .get()
            .getPlan();

    return evaluatePendingOrStartingStatusCode(deploymentPlan,
        ServiceStatusCode.DEPLOYING_PENDING,
        ServiceStatusCode.DEPLOYING_STARTING,
        ServiceStatusCode.DEPLOYING_PENDING.priority);
  }

  private ServiceStatusEvaluationStage evaluatePendingOrStartingStatusCode(Plan evaluatePlan,
                                                                          ServiceStatusCode pending,
                                                                          ServiceStatusCode starting,
                                                                          final int priority)
  {
    String reason;
    Optional<ServiceStatusCode> statusCode;

    int totalSteps = evaluatePlan.getChildren().stream()
        .flatMap(phase -> phase.getChildren().stream())
        .collect(Collectors.toSet())
        .size();

    int pendingSteps = evaluatePlan.getChildren().stream()
        .flatMap(phase -> phase.getChildren().stream())
        .filter(step -> step.isPending())
        .collect(Collectors.toSet())
        .size();

    int preparedSteps = evaluatePlan.getChildren().stream()
        .flatMap(phase -> phase.getChildren().stream())
        .filter(step -> step.isPrepared())
        .collect(Collectors.toSet())
        .size();

    int startingSteps = evaluatePlan.getChildren().stream()
        .flatMap(phase -> phase.getChildren().stream())
        .filter(step -> step.isStarting())
        .collect(Collectors.toSet())
        .size();

    int startedSteps = evaluatePlan.getChildren().stream()
        .flatMap(phase -> phase.getChildren().stream())
        .filter(step -> step.isStarted())
        .collect(Collectors.toSet())
        .size();

    int completedSteps = evaluatePlan.getChildren().stream()
        .flatMap(phase -> phase.getChildren().stream())
        .filter(step -> step.isComplete())
        .collect(Collectors.toSet())
        .size();

    // We're biasing pessimistically here, pick cases that are halting the
    // deployment from becoming complete.
    if (pendingSteps > 0 || preparedSteps > 0) {
      statusCode = Optional.of(pending);
    } else if (startingSteps > 0 || startedSteps > 0) {
      statusCode = Optional.of(starting);
    } else {
      // Implies deployment is complete.
      statusCode = Optional.empty();
    }

    String statusCodeString;
    if (statusCode.isPresent()) {
      statusCodeString = String.format("Status Code %s is TRUE,", statusCode.get().statusCode);
    } else {
      statusCodeString = String.format("Status Code %s and %s are both FALSE",
          pending.statusCode,
          starting.statusCode);
    }

    reason = String.format("Priority %d. %s Steps: Total(%d) Pending(%d) Prepared(%d) Starting(%d) Started(%d) Completed(%d)",
        priority,
        statusCodeString,
        totalSteps,
        pendingSteps,
        preparedSteps,
        startingSteps,
        startedSteps,
        completedSteps);

    return new ServiceStatusEvaluationStage(statusCode, reason);
  }

  private ServiceStatusEvaluationStage isDeploymentComplete(Optional<ServiceStatusCode> initializing) {

    String reason;
    Optional<ServiceStatusCode> statusCode;

    if (initializing.isPresent()) {
      reason = String.format("Priority %d. Status Code %s is FALSE. Service still initializing.",
          ServiceStatusCode.RUNNING.priority,
          ServiceStatusCode.RUNNING.statusCode);
      statusCode = Optional.empty();
    } else {
      // Check if the deployment Plan is complete.
      boolean isDeployPlanComplete = planCoordinator.getPlanManagers()
              .stream()
              .filter(planManager -> planManager.getPlan().isDeployPlan())
              .findFirst()
              .get()
              .getPlan()
              .isComplete();

      if (isDeployPlanComplete) {
        reason = String.format("Priority %d. Status Code %s is TRUE. Service deploy plan is complete.",
              ServiceStatusCode.RUNNING.priority,
              ServiceStatusCode.RUNNING.statusCode);
        statusCode = Optional.of(ServiceStatusCode.RUNNING);
      } else {
        reason = String.format("Priority %d. Status Code %s is FALSE. Service deploy plan is NOT complete.",
              ServiceStatusCode.RUNNING.priority,
              ServiceStatusCode.RUNNING.statusCode);
        statusCode = Optional.empty();
      }
    }
    return new ServiceStatusEvaluationStage(statusCode, reason);
  }

  private ServiceStatusEvaluationStage isErrorCreatingService() {

    String reason;
    Optional<ServiceStatusCode> statusCode;
    ServiceStatusCode errorCreatingService = ServiceStatusCode.ERROR_CREATING_SERVICE;
    boolean isAnyErrors = planCoordinator.getPlanManagers()
        .stream()
        .anyMatch(planManager -> !(planManager.getPlan().getErrors().isEmpty()));

    if (isAnyErrors) {
      reason = String.format("Priority %d. Status Code %s is TRUE. Errors found in plans.",
          errorCreatingService.priority,
          errorCreatingService.statusCode);
      statusCode = Optional.of(errorCreatingService);
    } else {
      reason = String.format("Priority %d. Status Code %s is FALSE. No errors found in plans.",
          errorCreatingService.priority,
          errorCreatingService.statusCode);
      statusCode = Optional.empty();
    }

    return new ServiceStatusEvaluationStage(statusCode, reason);
  }

  private ServiceStatusEvaluationStage isServiceInitializing() {
    String reason;
    Optional<ServiceStatusCode> statusCode;
    Optional<Protos.FrameworkID> frameworkId;
    ServiceStatusCode initializing = ServiceStatusCode.INITIALIZING;
    if (frameworkStore.isPresent()) {
      try {
        // fetchFrameWorkId can throw a StateStoreException.
        frameworkId = frameworkStore.get().fetchFrameworkId();
      } catch (StateStoreException e) {
        // regardless of the exception thrown, consider service as not-initialized.
        frameworkId = Optional.empty();
      }
    } else {
      frameworkId = Optional.empty();
    }

    if (frameworkId.isPresent()) {
      reason = String.format("Priority %d. Status Code %s is FALSE. Registered with Framework ID %s.",
              initializing.priority,
              initializing.statusCode,
              frameworkId.get().getValue());
      statusCode = Optional.empty();
    } else {
      reason = String.format("Priority %d. Status Code %s is TRUE. Mesos registration pending, no Framework ID found.",
              initializing.priority,
              initializing.statusCode);
      statusCode = Optional.of(initializing);
    }

    return new ServiceStatusEvaluationStage(statusCode, reason);
  }

  /**
   * Wrapper class to combine the {@link ServiceStatusCode} and a reason behind it.
   */
  private static class ServiceStatusEvaluationStage {

    private final Optional<ServiceStatusCode> serviceStatusCode;

    private final String statusReason;

    public ServiceStatusEvaluationStage(Optional<ServiceStatusCode> serviceStatusCode,
                                        String statusReason)
    {
      this.serviceStatusCode = serviceStatusCode;
      this.statusReason = statusReason;
    }

    public Optional<ServiceStatusCode> getServiceStatusCode() {
      return serviceStatusCode;
    }

    public String getStatusReason() {
      return statusReason;
    }
  }

  /**
   * Wrapper class to combine the {@link ServiceStatusCode} and {@link Response}
   * which can be exposed to testing code.
   */
  @VisibleForTesting
  protected static class ServiceStatusResult {

    private final Optional<ServiceStatusCode> serviceStatusCode;

    private final Response serviceStatusResponse;

    public ServiceStatusResult(Optional<ServiceStatusCode> serviceStatusCode,
                               Response serviceStatusResponse)
    {
      this.serviceStatusCode = serviceStatusCode;
      this.serviceStatusResponse = serviceStatusResponse;
    }

    public Optional<ServiceStatusCode> getServiceStatusCode() {
      return serviceStatusCode;
    }

    public Response getServiceStatusResponse() {
      return serviceStatusResponse;
    }
  }
}
