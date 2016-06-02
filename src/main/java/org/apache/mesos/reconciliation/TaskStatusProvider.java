package org.apache.mesos.reconciliation;

import java.util.Set;
import org.apache.mesos.Protos.TaskStatus;

/**
 * Interface for classes which can Provide a Set of TaskStatus objects.
 */
public interface TaskStatusProvider {
  Set<TaskStatus> getTaskStatuses() throws Exception;
}
