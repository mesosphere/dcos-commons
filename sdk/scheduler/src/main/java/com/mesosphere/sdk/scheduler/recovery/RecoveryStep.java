package com.mesosphere.sdk.scheduler.recovery;

import com.mesosphere.sdk.scheduler.plan.DeploymentStep;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.state.StateStore;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import java.util.Objects;
import java.util.Optional;

/**
 * An extension of {@link DeploymentStep} meant for use with {@link DefaultRecoveryPlanManager}.
 */
public class RecoveryStep extends DeploymentStep {

  public RecoveryStep(
      String name,
      PodInstanceRequirement podInstanceRequirement,
      StateStore stateStore)
  {
    this(name, podInstanceRequirement, stateStore, Optional.empty());
  }

  public RecoveryStep(
      String name,
      PodInstanceRequirement podInstanceRequirement,
      StateStore stateStore,
      Optional<String> namespace)
  {
    super(name, podInstanceRequirement, stateStore, namespace);
  }

  @Override
  public void start() {
    if (podInstanceRequirement.getRecoveryType().equals(RecoveryType.PERMANENT)) {
      FailureUtils.setPermanentlyFailed(stateStore, podInstanceRequirement.getPodInstance());
    }
  }

  public RecoveryType getRecoveryType() {
    return podInstanceRequirement.getRecoveryType();
  }

  @Override
  public String getMessage() {
    return String.format("%s RecoveryType: %s", super.getMessage(), getRecoveryType().name());
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
