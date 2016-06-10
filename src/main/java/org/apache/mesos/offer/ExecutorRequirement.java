package org.apache.mesos.offer;

import java.util.Collection;

import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.executor.ExecutorUtils;

/**
 * An ExecutorRequirement encapsulates the needed resources an Executor must have.
 */
public class ExecutorRequirement {
  private ExecutorInfo executorInfo;
  private Collection<Resource> resources;
  private Collection<ResourceRequirement> resourceRequirements;

  public ExecutorRequirement(ExecutorInfo executorInfo) {
    this.executorInfo = populateExecutorId(executorInfo);
    this.resources = getExecutorResources(executorInfo);
    this.resourceRequirements = RequirementUtils.getResourceRequirements(resources);
  }

  private ExecutorInfo populateExecutorId(ExecutorInfo executorInfo) {
    return ExecutorInfo.newBuilder(executorInfo)
      .setExecutorId(ExecutorUtils.toExecutorId(executorInfo.getName())).build();
  }

  public ExecutorInfo getExecutorInfo() {
    return executorInfo;
  }

  public Collection<ResourceRequirement> getResourceRequirements() {
    return resourceRequirements;
  }

  public Collection<String> getResourceIds() {
    return RequirementUtils.getResourceIds(getResourceRequirements());
  }

  public Collection<String> getPersistenceIds() {
    return RequirementUtils.getPersistenceIds(getResourceRequirements());
  }

  public boolean desiresResources() {
    for (ResourceRequirement resReq : resourceRequirements) {
      if (resReq.reservesResource()) {
        return true;
      }
    }

    return false;
  }

  private Collection<Resource> getExecutorResources(ExecutorInfo executorInfo) {
    return executorInfo.getResourcesList();
  }
}
