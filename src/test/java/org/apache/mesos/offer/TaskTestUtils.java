package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.protobuf.TaskInfoBuilder;

import java.util.Arrays;
import java.util.List;

/**
 * Created by gabriel on 6/15/16.
 */
public class TaskTestUtils {
    public static Protos.TaskInfo getTaskInfo(Protos.Resource resource) {
        return getTaskInfo(Arrays.asList(resource));
    }

    public static Protos.TaskInfo getTaskInfo(List<Protos.Resource> resources) {
        TaskInfoBuilder builder = new TaskInfoBuilder(
                ResourceTestUtils.testTaskId,
                ResourceTestUtils.testTaskName,
                ResourceTestUtils.testSlaveId);

        return builder.addAllResources(resources).build();
    }
}
