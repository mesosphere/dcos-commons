package org.apache.mesos.offer;

import java.util.Collection;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;

/**
 * A TaskRequirement encapsulates the needed resources a Task must have.
 */
public class TaskRequirement {
  private TaskInfo taskInfo;
  private Collection<Resource> resources;
  private Collection<ResourceRequirement> resourceRequirements;

  public TaskRequirement(TaskInfo taskInfo) throws InvalidTaskRequirementException {
    validateTaskInfo(taskInfo);
    taskInfo = TaskInfo.newBuilder(taskInfo)
      .setTaskId(TaskUtils.toTaskId(taskInfo.getName())).build();

    this.taskInfo = taskInfo;
    this.resources = getTaskResources(taskInfo);
    this.resourceRequirements = RequirementUtils.getResourceRequirements(resources);
  }

  private void validateTaskInfo(TaskInfo taskInfo) throws InvalidTaskRequirementException {
    if (taskInfo.hasExecutor()) {
      throw new InvalidTaskRequirementException(
          "TaskInfo must not contain ExecutorInfo. "
        + "Use ExecutorRequirement to encapsulate Executor requirements.");
    }
  }

  public TaskInfo getTaskInfo() {
    return taskInfo;
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

  private Collection<Resource> getTaskResources(TaskInfo taskInfo) {
    return taskInfo.getResourcesList();
  }

 /**
  * An InvalidTaskRequirement exception is thrown when an attempt is made to intialize a
  * TaskRequirement with invalid arguments.
  */
  public static class InvalidTaskRequirementException extends Exception {
    public InvalidTaskRequirementException(String msg) {
      super(msg);
    }
  }
}
