package org.apache.mesos.offer;

import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskInfo;

import java.util.List;

/**
 * PlacementStrategy Inteface.
 * Inteface should provide lists of Agents to avoid or colocate with
 * depending upon the provided TaskInfo
 */
public interface PlacementStrategy {
  List<SlaveID> getAgentsToAvoid(TaskInfo taskInfo);

  List<SlaveID> getAgentsToColocate(TaskInfo taskInfo);
}
