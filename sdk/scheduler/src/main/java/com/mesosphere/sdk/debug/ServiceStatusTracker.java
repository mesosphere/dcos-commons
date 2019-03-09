package com.mesosphere.sdk.debug;

import com.mesosphere.sdk.http.ResponseUtils;
import com.mesosphere.sdk.scheduler.plan.Plan;
import com.mesosphere.sdk.scheduler.plan.PlanCoordinator;
import com.mesosphere.sdk.state.FrameworkStore;
import com.mesosphere.sdk.state.StateStore;

import org.apache.mesos.Protos;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.core.Response;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ServiceStatusTracker is the backend of [ServiceStatusResource].
 * It returns a single code representing the status of the service
 */
public class ServiceStatusTracker {

  /**
   * ServiceStatusCode encodes the service status code.
   */
  public enum ServiceStatusCode {

    INITIALIZING(418),
    RUNNING(200),
    DEPLOYING_PENDING(204),
    DEPLOYING_STARTING(205),
    DEGRADED(206),
    RECOVERING_PENDING(203),
    RECOVERING_STARTING(205),
    BACKING_UP(420),
    RESTORING(421),
    UPGRADE_ROLLBACK_DOWNGRADE(426),
    SERVICE_UNAVAILABLE(503);

    private final int statusCode;

    ServiceStatusCode(int statusCode) {
      this.statusCode = statusCode;
    }
  }

  private static final String RESULT_CODE_KEY = "value";

  private static final String BACKUP_PLAN_CONTAINS = "backup";

  private static final String RESTORE_PLAN_CONTAINS = "restore";

  private final StateStore stateStore;

  private final PlanCoordinator planCoordinator;

  private final FrameworkStore frameworkStore;

  // SUPPRESS CHECKSTYLE LineLengthCheck
  public ServiceStatusTracker(PlanCoordinator planCoordinator, StateStore stateStore, FrameworkStore frameworkStore) {
    this.planCoordinator = planCoordinator;
    this.stateStore = stateStore;
    this.frameworkStore = frameworkStore;
  }

  public Response getJson(boolean isVerbose) {
    /*
     * Return status codes with the following priority.
     * HTTP Response Code, Priority, Reason
     * 418, 1, Initializing
     * 200, 1, Running
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

    // Final response object we're going to return.
    JSONObject response = new JSONObject();

    Optional<ServiceStatusCode> serviceStatusCode = Optional.empty();
    JSONArray statusCodeReasons = new JSONArray();

    ServiceStatusEvaluationStage initializing = isServiceInitializing();
    if (isVerbose) {
      statusCodeReasons.put(initializing.getStatusReason());
    }

    // SUPPRESS CHECKSTYLE LineLengthCheck
    ServiceStatusEvaluationStage deploymentComplete = isDeploymentComplete(initializing.getServiceStatusCode());
    if (isVerbose) {
      statusCodeReasons.put(deploymentComplete.getStatusReason());
    }

    // SUPPRESS CHECKSTYLE LineLengthCheck
    ServiceStatusEvaluationStage isDeploying = isDeploying();
    if (isVerbose) {
      statusCodeReasons.put(isDeploying.getStatusReason());
    }

    ServiceStatusEvaluationStage isDegraded = isDegraded();
    if (isVerbose) {
      statusCodeReasons.put(isDegraded.getStatusReason());
    }

    ServiceStatusEvaluationStage isRecovering = isRecovering();
    if (isVerbose) {
      statusCodeReasons.put(isRecovering.getStatusReason());
    }

    ServiceStatusEvaluationStage isBackingUp = isBackingUp();
    if (isVerbose) {
      statusCodeReasons.put(isBackingUp.getStatusReason());
    }

    ServiceStatusEvaluationStage isRestoring = isRestoring();
    if (isVerbose) {
      statusCodeReasons.put(isRestoring.getStatusReason());
    }

    //TODO(kjoshi): Implement Upgrade/Rollback/Downgrade.

    //Set return statusCode to initializing if present.
    if (initializing.getServiceStatusCode().isPresent()) {
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

    //These are low-priority codes that require a deployment to be complete.
    if (deploymentComplete.getServiceStatusCode().isPresent()) {
      if (isRestoring.getServiceStatusCode().isPresent()) {
        serviceStatusCode = isRestoring.getServiceStatusCode();
      } else if (isBackingUp.getServiceStatusCode().isPresent()) {
        serviceStatusCode = isBackingUp.getServiceStatusCode();
      }
    }

    if (serviceStatusCode.isPresent()) {
      response.put(RESULT_CODE_KEY, serviceStatusCode.get().statusCode);
    } else {
      response.put(RESULT_CODE_KEY, ServiceStatusCode.SERVICE_UNAVAILABLE.statusCode);
    }

    if (isVerbose) {
      response.put("reasons:", statusCodeReasons);
    }

    return ResponseUtils.jsonOkResponse(response);
  }

  private ServiceStatusEvaluationStage isRestoring() {
    String reason;
    Optional<ServiceStatusCode> statusCode;

    Set<Plan> restorePlans = planCoordinator.getPlanManagers()
        .stream()
        .filter(planManager -> planManager.getPlan().getName().contains(RESTORE_PLAN_CONTAINS))
        .map(planManager -> planManager.getPlan())
        .collect(Collectors.toSet());

    if (restorePlans.isEmpty()) {
      // SUPPRESS CHECKSTYLE LineLengthCheck
      reason = String.format("Priority 5. Status Code %s is FALSE. No restore plans detected.",
          ServiceStatusCode.RESTORING.statusCode);
      statusCode = Optional.empty();
    } else {
      //Found plan name with "restore" in it, check if any are running, if so get their names.
      Set<String> runningRestorePlans = restorePlans.stream()
          .filter(plan -> plan.isRunning())
          .map(plan -> plan.getName())
          .collect(Collectors.toSet());

      if (runningRestorePlans.isEmpty()) {
        Set<String> notRunningRestorePlans = restorePlans.stream()
            .map(plan -> plan.getName())
            .collect(Collectors.toSet());

        // SUPPRESS CHECKSTYLE LineLengthCheck
        reason = String.format("Priority 5. Status Code %s is FALSE. Following restore plans not running: %s,",
            ServiceStatusCode.RESTORING.statusCode, String.join(", ", notRunningRestorePlans));
        statusCode = Optional.empty();
      } else {
        //Found running backup plans.
        // SUPPRESS CHECKSTYLE LineLengthCheck
        reason = String.format("Priority 5. Status Code %s is TRUE. Following restore plans found running: %s.",
            ServiceStatusCode.RESTORING.statusCode, String.join(", ", runningRestorePlans));
        statusCode = Optional.of(ServiceStatusCode.RESTORING);
      }
    }
    return new ServiceStatusEvaluationStage(statusCode, reason);
  }

  private ServiceStatusEvaluationStage isBackingUp() {
    String reason;
    Optional<ServiceStatusCode> statusCode;

    Set<Plan> backupPlans = planCoordinator.getPlanManagers()
        .stream()
        .filter(planManager -> planManager.getPlan().getName().contains(BACKUP_PLAN_CONTAINS))
        .map(planManager -> planManager.getPlan())
        .collect(Collectors.toSet());

    if (backupPlans.isEmpty()) {
      // SUPPRESS CHECKSTYLE LineLengthCheck
      reason = String.format("Priority 5. Status Code %s is FALSE. No backup plans detected.",
          ServiceStatusCode.BACKING_UP.statusCode);
      statusCode = Optional.empty();
    } else {
      //Found plan name with "backup" in it, check if any are running, if so get their names.
      Set<String> runningBackupPlans = backupPlans.stream()
          .filter(plan -> plan.isRunning())
          .map(plan -> plan.getName())
          .collect(Collectors.toSet());

      if (runningBackupPlans.isEmpty()) {
        Set<String> notRunningBackupPlans = backupPlans.stream()
            .map(plan -> plan.getName())
            .collect(Collectors.toSet());

        // SUPPRESS CHECKSTYLE LineLengthCheck
        reason = String.format("Priority 5. Status Code %s is FALSE. Following backup plans not running: %s",
            ServiceStatusCode.BACKING_UP.statusCode, String.join(", ", notRunningBackupPlans));
        statusCode = Optional.empty();
      } else {
        //Found running backup plans.
        // SUPPRESS CHECKSTYLE LineLengthCheck
        reason = String.format("Priority 5. Status Code %s is TRUE. Following backup plans found running: %s",
            ServiceStatusCode.BACKING_UP.statusCode, String.join(", ", runningBackupPlans));
        statusCode = Optional.of(ServiceStatusCode.BACKING_UP);
      }
    }
    return new ServiceStatusEvaluationStage(statusCode, reason);
  }

  private ServiceStatusEvaluationStage isRecovering() {
    String reason;
    Optional<ServiceStatusCode> statusCode;

    //Get the recovery plan.
    Plan recoveryPlan = planCoordinator.getPlanManagers()
            .stream()
            .filter(planManager -> planManager.getPlan().isRecoveryPlan())
            .findFirst()
            .get()
            .getPlan();

    if (recoveryPlan.isComplete()) {
      // SUPPRESS CHECKSTYLE LineLengthCheck
      reason = String.format("Priority 4. Status Code %s and %s is FALSE. Recovery plan is complete.",
          ServiceStatusCode.RECOVERING_PENDING.statusCode,
          ServiceStatusCode.RECOVERING_STARTING.statusCode);
      statusCode = Optional.empty();
    } else {

      //Recovery plan is NOT complete.
      int totalSteps = recoveryPlan.getChildren().stream()
          .flatMap(phase -> phase.getChildren().stream())
          .collect(Collectors.toSet())
          .size();

      int pendingSteps = recoveryPlan.getChildren().stream()
          .flatMap(phase -> phase.getChildren().stream())
          .filter(step -> step.isPending())
          .collect(Collectors.toSet())
          .size();

      int preparedSteps = recoveryPlan.getChildren().stream()
          .flatMap(phase -> phase.getChildren().stream())
          .filter(step -> step.isPrepared())
          .collect(Collectors.toSet())
          .size();

      int startingSteps = recoveryPlan.getChildren().stream()
          .flatMap(phase -> phase.getChildren().stream())
          .filter(step -> step.isStarting())
          .collect(Collectors.toSet())
          .size();

      int startedSteps = recoveryPlan.getChildren().stream()
          .flatMap(phase -> phase.getChildren().stream())
          .filter(step -> step.isStarted())
          .collect(Collectors.toSet())
          .size();

      int completedSteps = recoveryPlan.getChildren().stream()
          .flatMap(phase -> phase.getChildren().stream())
          .filter(step -> step.isComplete())
          .collect(Collectors.toSet())
          .size();

      //We're biasing pessimistically here, pick cases that are halting the
      // deployment from becoming complete.
      if (pendingSteps > 0 || preparedSteps > 0) {
        statusCode = Optional.of(ServiceStatusCode.RECOVERING_PENDING);
      } else if (startingSteps > 0 || startedSteps > 0) {
        statusCode = Optional.of(ServiceStatusCode.RECOVERING_STARTING);
      } else {
        //Implies deployment is complete.
        statusCode = Optional.empty();
      }

      String statusCodeString;

      if (statusCode.isPresent()) {
        // SUPPRESS CHECKSTYLE MultipleStringLiteralsCheck
        statusCodeString = String.format("Status Code %s is TRUE,", statusCode.get().statusCode);
      } else {
        // SUPPRESS CHECKSTYLE MultipleStringLiteralsCheck
        statusCodeString = String.format("Status Code %s and %s are both FALSE",
            ServiceStatusCode.RECOVERING_PENDING.statusCode,
            ServiceStatusCode.RECOVERING_STARTING.statusCode);
      }

      // SUPPRESS CHECKSTYLE LineLengthCheck
      reason = String.format("Priority 4. %s Steps: Total(%d) Pending(%d) Prepared(%d) Starting(%d) Started(%d) Completed(%d)",
          statusCodeString,
          totalSteps,
          pendingSteps,
          preparedSteps,
          startingSteps,
          startedSteps,
          completedSteps);
    }

    return new ServiceStatusEvaluationStage(statusCode, reason);
  }


  private ServiceStatusEvaluationStage isDegraded() {

    String reason = String.format("Priority 3. Status Code %s is FALSE, Not implemented yet.",
        ServiceStatusCode.DEGRADED.statusCode);
    Optional<ServiceStatusCode> statusCode = Optional.empty();

    return new ServiceStatusEvaluationStage(statusCode, reason);
  }

  private ServiceStatusEvaluationStage isDeploying() {

    String reason;
    Optional<ServiceStatusCode> statusCode;

    //Get the deployment plan.
    Plan deploymentPlan = planCoordinator.getPlanManagers()
            .stream()
            .filter(planManager -> planManager.getPlan().isDeployPlan())
            .findFirst()
            .get()
            .getPlan();

    int totalSteps = deploymentPlan.getChildren().stream()
        .flatMap(phase -> phase.getChildren().stream())
        .collect(Collectors.toSet())
        .size();

    int pendingSteps = deploymentPlan.getChildren().stream()
        .flatMap(phase -> phase.getChildren().stream())
        .filter(step -> step.isPending())
        .collect(Collectors.toSet())
        .size();

    int preparedSteps = deploymentPlan.getChildren().stream()
        .flatMap(phase -> phase.getChildren().stream())
        .filter(step -> step.isPrepared())
        .collect(Collectors.toSet())
        .size();

    int startingSteps = deploymentPlan.getChildren().stream()
        .flatMap(phase -> phase.getChildren().stream())
        .filter(step -> step.isStarting())
        .collect(Collectors.toSet())
        .size();

    int startedSteps = deploymentPlan.getChildren().stream()
        .flatMap(phase -> phase.getChildren().stream())
        .filter(step -> step.isStarted())
        .collect(Collectors.toSet())
        .size();

    int completedSteps = deploymentPlan.getChildren().stream()
        .flatMap(phase -> phase.getChildren().stream())
        .filter(step -> step.isComplete())
        .collect(Collectors.toSet())
        .size();

    //We're biasing pessimistically here, pick cases that are halting the
    // deployment from becoming complete.
    if (pendingSteps > 0 || preparedSteps > 0) {
      statusCode = Optional.of(ServiceStatusCode.DEPLOYING_PENDING);
    } else if (startingSteps > 0 || startedSteps > 0) {
      statusCode = Optional.of(ServiceStatusCode.DEPLOYING_STARTING);
    } else {
      //Implies deployment is complete.
      statusCode = Optional.empty();
    }

    String statusCodeString;
    if (statusCode.isPresent()) {
      statusCodeString = String.format("Status Code %s is TRUE,", statusCode.get().statusCode);
    } else {
      statusCodeString = String.format("Status Code %s and %s are both FALSE",
          ServiceStatusCode.DEPLOYING_PENDING.statusCode,
          ServiceStatusCode.DEPLOYING_STARTING.statusCode);
    }

    // SUPPRESS CHECKSTYLE LineLengthCheck
    reason = String.format("Priority 2. %s Steps: Total(%d) Pending(%d) Prepared(%d) Starting(%d) Started(%d) Completed(%d)",
        statusCodeString,
        totalSteps,
        pendingSteps,
        preparedSteps,
        startingSteps,
        startedSteps,
        completedSteps);

    return new ServiceStatusEvaluationStage(statusCode, reason);
  }

  // SUPPRESS CHECKSTYLE LineLengthCheck
  private ServiceStatusEvaluationStage isDeploymentComplete(Optional<ServiceStatusCode> initializing) {

    String reason;
    Optional<ServiceStatusCode> statusCode;

    if (initializing.isPresent()) {
      reason = String.format("Priority 1. Status Code %s is FALSE. Service still initializing.",
          ServiceStatusCode.RUNNING.statusCode);
      statusCode = Optional.empty();
    } else {
      //Check if the deployment Plan is complete.
      boolean isDeployPlanComplete = planCoordinator.getPlanManagers()
              .stream()
              .filter(planManager -> planManager.getPlan().isDeployPlan())
              .findFirst()
              .get()
              .getPlan()
              .isComplete();

      if (isDeployPlanComplete) {
        // SUPPRESS CHECKSTYLE LineLengthCheck
        reason = String.format("Priority 1. Status Code %s is TRUE. Service deploy plan is complete.",
              ServiceStatusCode.RUNNING.statusCode);
        statusCode = Optional.of(ServiceStatusCode.RUNNING);
      } else {
        // SUPPRESS CHECKSTYLE LineLengthCheck
        reason = String.format("Priority 1. Status Code %s is FALSE. Service deploy plan is NOT complete.",
              ServiceStatusCode.RUNNING.statusCode);
        statusCode = Optional.empty();
      }
    }
    return new ServiceStatusEvaluationStage(statusCode, reason);
  }

  private ServiceStatusEvaluationStage isServiceInitializing() {

    Optional<Protos.FrameworkID> frameworkId = frameworkStore.fetchFrameworkId();
    String reason;
    Optional<ServiceStatusCode> statusCode;

    if (frameworkId.isPresent()) {
      // SUPPRESS CHECKSTYLE LineLengthCheck
      reason = String.format("Priority 1. Status Code %s is FALSE. Registered with Framework ID %s.",
              ServiceStatusCode.INITIALIZING.statusCode, frameworkId.get().getValue());
      statusCode = Optional.empty();
    } else {
      // SUPPRESS CHECKSTYLE LineLengthCheck
      reason = String.format("Priority 1. Status Code %s is TRUE. Mesos registration pending, no Framework ID found.",
              ServiceStatusCode.INITIALIZING.statusCode);
      statusCode = Optional.of(ServiceStatusCode.INITIALIZING);
    }

    return new ServiceStatusEvaluationStage(statusCode, reason);
  }

  /**
   * Wrapper class to combine the [ServiceStatusCode] and a reason behind it.
   */
  public static class ServiceStatusEvaluationStage {

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
}
