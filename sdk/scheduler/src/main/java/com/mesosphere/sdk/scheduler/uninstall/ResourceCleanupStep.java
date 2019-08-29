package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.scheduler.plan.Status;

import java.util.Set;

/**
 * Step which implements the uninstalling of a particular reserved resource. For instance, persistent volumes and cpu.
 */
public class ResourceCleanupStep extends UninstallStep {

  private final String resourceId;

  /**
   * Creates a new instance with the provided {@code resourceId} and initial {@code status}.
   */
  public ResourceCleanupStep(String resourceId) {
    // Avoid having the step name be a pure UUID. Otherwise PlansResource will confuse this UUID with the step UUID:
    super("unreserve-" + resourceId);
    this.resourceId = resourceId;
  }

  @Override
  public void start() {
    if (isPending()) {
      logger.info("Setting state to Prepared for resource {}", resourceId);
      setStatus(Status.PREPARED);
    }
  }

  /**
   * Notifies this step that some resource ids are about to be unreserved. If one of the resource ids is relevant to
   * this step, the step's status will be set to {@code COMPLETE}.
   */
  public void updateResourceStatus(Set<String> uninstalledResourceIds) {
    if (uninstalledResourceIds.contains(resourceId)) {
      setStatus(Status.COMPLETE);
    }
  }
}
