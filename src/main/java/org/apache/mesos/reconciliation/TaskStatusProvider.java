package org.apache.mesos.reconciliation;

import java.util.Set;
import org.apache.mesos.Protos.TaskStatus;

/**
 * Interface for classes which can Provide a Set of {@link TaskStatus} objects.
 */
public interface TaskStatusProvider {
    /**
     * Returns a {@link Set} of zero or more {@link TaskStatus}es describing available running
     * scheduler tasks.
     *
     * @throws Exception in the event of a retrieval error
     */
    Set<TaskStatus> getTaskStatuses() throws Exception;
}
