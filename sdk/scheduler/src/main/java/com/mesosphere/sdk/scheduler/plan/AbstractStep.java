package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.plan.backoff.Backoff;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides a default implementation of commonly-used {@link Step} logic.
 */
public abstract class AbstractStep implements Step {

  /**
   * Non-static to ensure that we inherit the names of subclasses.
   */
  protected final Logger logger;

  protected UUID id = UUID.randomUUID();

  private final String name;

  private final Object statusLock = new Object();

  private Status status;

  private boolean interrupted;

  protected AbstractStep(String name, Optional<String> namespace) {
    this.logger = LoggingUtils.getLogger(getClass(), namespace);
    this.name = name;
    this.status = Status.PENDING;
    this.interrupted = false;
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Status getStatus() {
    synchronized (statusLock) {
      if (interrupted && (status == Status.PENDING || status == Status.PREPARED)) {
        return Status.WAITING;
      }
      /**
       * All steps from all yaml plans are {@link DeploymentStep}s. Only {@link DeploymentStep} and
       * {@link com.mesosphere.sdk.scheduler.recovery.RecoveryStep}s are relevant for backoff delay. As
       * {@link com.mesosphere.sdk.scheduler.recovery.RecoveryStep}s are always constructed on the fly, we don't need
       * to mutate their state. However, {@link DeploymentStep}s are built only once and are used until they are
       * complete. The {@link DeploymentStep#setStatus(Status)} is called from other places as well upon external
       * triggers and the same method is called here to indicate that the step can make progress.
       *
       * However, we don't check for sub class hierarchy explicitly as we want this functionality to be applicable for
       * all sub classes. If a sub class skips the {@link Status#DELAYED} (by overriding the
       * {@link #update(org.apache.mesos.Protos.TaskStatus)} method), then those should be unaffected by this.
       */
      if (status == Status.DELAYED) {
        getPodInstanceRequirement().ifPresent(podInstanceReq -> {
          boolean noDelay = podInstanceReq
                  .getTasksToLaunch()
                  .stream()
                  .noneMatch(taskName -> Backoff
                          .getInstance()
                          .getDelay(CommonIdUtils.getTaskInstanceName(podInstanceReq.getPodInstance(), taskName))
                          .isPresent());
          if (noDelay) {
            // Step was DELAYED previously but the configured delay has elapsed now - move back to PENDING
            setStatus(Status.PENDING);
          }
        });
      }
      return status;
    }
  }

  /**
   * Updates the status setting and logs the outcome. Should only be called either by tests, by
   * {@code this}, or by subclasses.
   *
   * @param newStatus the new status to be set
   */
  protected void setStatus(Status newStatus) {
    Status oldStatus;
    synchronized (statusLock) {
      oldStatus = status;
      status = newStatus;
      logger.info("{}: changed status from: {} to: {} (interrupted={})",
          getName(), oldStatus, newStatus, interrupted);
    }
  }

  @Override
  public void interrupt() {
    synchronized (statusLock) {
      interrupted = true;
    }
  }

  @Override
  public void proceed() {
    synchronized (statusLock) {
      interrupted = false;
    }
  }

  @Override
  public boolean isInterrupted() {
    synchronized (statusLock) {
      return interrupted;
    }
  }

  /**
   * Restarts the step involves two steps:
   *  1. Reset delay for any of its given tasks.
   *  2. Set its status back to {@link Status#PENDING}.
   */
  @Override
  public void restart() {
    logger.warn("Restarting step: '{} [{}]'", getName(), getId());
    getPodInstanceRequirement().ifPresent(podInstanceRequirement -> podInstanceRequirement
            .getTasksToLaunch()
            .forEach(taskName -> {
              String taskInstanceName = CommonIdUtils.getTaskInstanceName(
                      podInstanceRequirement.getPodInstance(), taskName);
              Backoff.getInstance().clearDelay(taskInstanceName);
            }));
    setStatus(Status.PENDING);
  }

  @Override
  public void forceComplete() {
    logger.warn("Forcing completion of step: '{} [{}]'", getName(), getId());
    setStatus(Status.COMPLETE);
  }

  @Override
  public String toString() {
    return ReflectionToStringBuilder.toString(this);
  }

  @Override
  public boolean equals(Object o) {
    return EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getId());
  }
}
