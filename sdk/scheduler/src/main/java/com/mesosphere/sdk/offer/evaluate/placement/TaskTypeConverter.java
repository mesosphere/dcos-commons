package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.mesos.Protos.TaskInfo;

/**
 * Given a {@link TaskInfo}, returns a type string for that task. This must be implemented by the
 * developer, or see {@link TaskTypeLabelConverter} for a sample implementation which expects a
 * {@link TaskInfo} Label which contains the task type.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
public interface TaskTypeConverter {
  String getTaskType(TaskInfo taskInfo);
}
