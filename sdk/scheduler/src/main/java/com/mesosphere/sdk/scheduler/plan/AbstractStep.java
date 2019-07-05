package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.plan.backoff.BackOff;

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
       * triggers and the same method is called here to indicate that the
       * step can make progress (or not).
       */
      if (status == Status.DELAYED && this instanceof DeploymentStep) {
        PodInstanceRequirement req = ((DeploymentStep) this).podInstanceRequirement;
        boolean noDelay = req
                .getTasksToLaunch()
                .stream()
                .allMatch(n -> BackOff
                        .getInstance()
                        .isReady(CommonIdUtils.getTaskInstanceName(req.getPodInstance(), n)));
        if (noDelay) {
          setStatus(Status.PENDING);
        }
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

  @Override
  public void restart() {
    logger.warn("Restarting step: '{} [{}]'", getName(), getId());
    //TODO@kjoshi: Reset backoff status here when issued by command line.
    //Implementation detail: Set backoff back to zero.
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
