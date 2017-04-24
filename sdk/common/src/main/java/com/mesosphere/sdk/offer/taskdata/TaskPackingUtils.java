package com.mesosphere.sdk.offer.taskdata;

import org.apache.mesos.Protos.ExecutorID;
import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.TaskInfo;

import com.google.protobuf.InvalidProtocolBufferException;

/**
 * Mesos requirements do not allow a TaskInfo to simultaneously have a Command and Executor.  In order to
 * workaround this we encapsulate a TaskInfo's Command field in an ExecutorInfo and store it in the data field of
 * the TaskInfo.
 *
 * Unpacked:
 * - taskInfo
 *   - executor
 *   - data: custom
 *   - command
 *
 * Packed:
 * - taskInfo
 *   - executor
 *   - data: serialized executorinfo
 *     - data: custom
 *     - command
 */
public class TaskPackingUtils {

    /** Used to mark packed task data within an {@link ExecutorInfo}. */
    private static final String COMMAND_DATA_PACKAGE_EXECUTORID = "command_data_package_executor";

    private TaskPackingUtils() {
        // do not instantiate
    }

    /**
     * Packs the TaskInfo in preparation for sending it to the Executor. For a description of how the packing works
     * (and why it exists), see the class-level javadoc.
     *
     * @see #unpack(TaskInfo)
     */
    public static TaskInfo pack(TaskInfo taskInfo) {
        if (!taskInfo.hasExecutor()) {
            return taskInfo;
        } else {
            ExecutorInfo.Builder executorInfoBuilder = ExecutorInfo.newBuilder()
                    .setExecutorId(ExecutorID.newBuilder().setValue(COMMAND_DATA_PACKAGE_EXECUTORID));

            if (taskInfo.hasCommand()) {
                executorInfoBuilder.setCommand(taskInfo.getCommand());
            }

            if (taskInfo.hasData()) {
                executorInfoBuilder.setData(taskInfo.getData());
            }

            return TaskInfo.newBuilder(taskInfo)
                    .setData(executorInfoBuilder.build().toByteString())
                    .clearCommand()
                    .build();
        }
    }

    /**
     * This method reverses the work done in {@link #packTaskInfo(TaskInfo)} such that the original
     * TaskInfo is regenerated. If the provided {@link TaskInfo} doesn't appear to have packed data
     * then this operation does nothing.
     *
     * @see #pack(TaskInfo)
     */
    public static TaskInfo unpack(TaskInfo taskInfo) {
        if (!taskInfo.hasData() || !taskInfo.hasExecutor()) {
            return taskInfo;
        } else {
            TaskInfo.Builder taskBuilder = TaskInfo.newBuilder(taskInfo);
            ExecutorInfo pkgExecutorInfo;
            try {
                pkgExecutorInfo = ExecutorInfo.parseFrom(taskInfo.getData());
            } catch (InvalidProtocolBufferException e) {
                // This TaskInfo has a data field, but it doesn't parse as an ExecutorInfo. Not a packed TaskInfo?
                // TODO(nickbp): This try/catch should be removed once CuratorStateStore is no longer speculatively
                //               unpacking all TaskInfos.
                return taskInfo;
            }

            if (pkgExecutorInfo.hasCommand()) {
                taskBuilder.setCommand(pkgExecutorInfo.getCommand());
            }

            if (pkgExecutorInfo.hasData()) {
                taskBuilder.setData(pkgExecutorInfo.getData());
            } else {
                taskBuilder.clearData();
            }

            return taskBuilder.build();
        }
    }
}
