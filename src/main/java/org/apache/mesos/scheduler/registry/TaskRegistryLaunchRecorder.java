package org.apache.mesos.scheduler.registry;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.OperationRecorder;

import java.util.List;

/**
 * Created by dgrnbrg on 7/20/16.
 */
public class TaskRegistryLaunchRecorder implements OperationRecorder {
    private Protos.TaskInfo latestInfo = null;

    @Override
    public void record(Protos.Offer.Operation operation, Protos.Offer offer) throws Exception {
        if (operation.getType().equals(Protos.Offer.Operation.Type.LAUNCH)) {
            List<Protos.TaskInfo> infos = operation.getLaunch().getTaskInfosList();
            if (infos.size() != 1) {
                throw new RuntimeException("Expected to launch a single task, but got " + infos);
            }
            latestInfo = infos.get(0);
        }
    }

    public Protos.TaskInfo getLatestInfo() {
        return latestInfo;
    }
}
