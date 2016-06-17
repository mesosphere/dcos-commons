package org.apache.mesos.offer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.apache.mesos.offer.TaskRequirement.InvalidTaskRequirementException;

import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskInfo;

/**
 * An OfferRequirement encapsulates the needed resources an Offer must have.
 * In general these are Resource requirements like it must have a certain amount of
 * cpu, memory, and disk.  Additionally it has two modes regarding expectations around
 * Persistent Volumes.  In the CREATE mode it anticipates that the Scheduler will be
 * creating the required volume, so a Volume with a particular persistence id is not
 * required to be already present in an Offer.  In the EXISTING mode, we expect that
 * an Offer will already have the indicated persistence ID.
 */
public class OfferRequirement {
  private Collection<SlaveID> avoidAgents;
  private Collection<SlaveID> colocateAgents;
  private Collection<TaskRequirement> taskRequirements;
  private ExecutorRequirement executorRequirement;

  public OfferRequirement(
    Collection<TaskInfo> taskInfos,
    ExecutorInfo execInfo,
    Collection<SlaveID> avoidAgents,
    Collection<SlaveID> colocateAgents)
    throws InvalidTaskRequirementException {

    this.taskRequirements = getTaskRequirementsInternal(taskInfos);

    if (execInfo != null) {
      this.executorRequirement = new ExecutorRequirement(execInfo);
    }

    if (avoidAgents == null) {
      this.avoidAgents = Collections.emptyList();
    } else {
      this.avoidAgents = avoidAgents;
    }

    if (colocateAgents == null) {
      this.colocateAgents = Collections.emptyList();
    } else {
      this.colocateAgents = colocateAgents;
    }
  }

  public OfferRequirement(Collection<TaskInfo> taskInfos) throws InvalidTaskRequirementException {
    this(taskInfos, null, Collections.emptyList(), Collections.emptyList());
  }

  public OfferRequirement(Collection<TaskInfo> taskInfos, ExecutorInfo execInfo)
    throws InvalidTaskRequirementException {
    this(taskInfos, execInfo, Collections.emptyList(), Collections.emptyList());
  }

  public Collection<TaskRequirement> getTaskRequirements() {
    return taskRequirements;
  }

  public ExecutorRequirement getExecutorRequirement() {
    return executorRequirement;
  }

  public Collection<SlaveID> getAvoidAgents() {
    return avoidAgents;
  }

  public Collection<SlaveID> getColocateAgents() {
    return colocateAgents;
  }

  public Collection<String> getResourceIds() {
    Collection<String> resourceIds = new ArrayList<String>();

    for (TaskRequirement taskReq : taskRequirements) {
      resourceIds.addAll(taskReq.getResourceIds());
    }

    if (executorRequirement != null)  {
      resourceIds.addAll(executorRequirement.getResourceIds());
    }

    return resourceIds;
  }

  public Collection<String> getPersistenceIds() {
    Collection<String> persistenceIds = new ArrayList<String>();

    for (TaskRequirement taskReq : taskRequirements) {
      persistenceIds.addAll(taskReq.getPersistenceIds());
    }

    if (executorRequirement != null)  {
      persistenceIds.addAll(executorRequirement.getPersistenceIds());
    }

    return persistenceIds;
  }

  private Collection<TaskRequirement> getTaskRequirementsInternal(Collection<TaskInfo> taskInfos)
    throws InvalidTaskRequirementException {

    Collection<TaskRequirement> taskRequirements = new ArrayList<TaskRequirement>();

    for (TaskInfo taskInfo : taskInfos) {
      taskRequirements.add(new TaskRequirement(taskInfo));
    }

    return taskRequirements;
  }
}
