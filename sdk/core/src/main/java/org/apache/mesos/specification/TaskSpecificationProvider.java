package org.apache.mesos.specification;

import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.offer.TaskException;

/**
 * Maps a provided {@link TaskInfo} to a {@link TaskSpecification}.
 */
public interface TaskSpecificationProvider {
    /**
     * Returns the {@link TaskSpecification} from which the provided {@link TaskInfo} was derived,
     * or throws a {@link TaskException} if retrieval failed.
     */
    public TaskSpecification getTaskSpecification(TaskInfo taskInfo) throws TaskException;
}
