package org.apache.mesos.offer;

import org.apache.mesos.Protos.SlaveID;
import org.apache.mesos.Protos.TaskInfo;

import java.util.List;

/**
 * PlacementStrategy indicating that any location is acceptable for a given TaskInfo.
 */
public class AnyPlacementStrategy implements PlacementStrategy {

  public List<SlaveID> getAgentsToAvoid(TaskInfo taskInfo) {
    return null;
  }

  public List<SlaveID> getAgentsToColocate(TaskInfo taskInfo) {
    return null;
  }
}
