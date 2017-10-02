package com.mesosphere.sdk.testing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.mesos.Protos;

public class ClusterState {
    private final List<Protos.Offer> sentOffers = new ArrayList<>();
    private final List<Collection<Protos.TaskInfo>> createdPods = new ArrayList<>();

    public void addSentOffer(Protos.Offer offer) {
        sentOffers.add(offer);
    }

    public Protos.Offer getLastOffer() {
        if (sentOffers.isEmpty()) {
            throw new IllegalStateException("No offers were sent yet");
        }
        return sentOffers.get(sentOffers.size() - 1);
    }

    public void addPod(Collection<Protos.TaskInfo> pod) {
        createdPods.add(pod);
    }

    public Collection<Protos.TaskInfo> getLastPod() {
        if (createdPods.isEmpty()) {
            throw new IllegalStateException("No pods were created yet");
        }
        return createdPods.get(createdPods.size() - 1);
    }

    public Protos.TaskID getTaskId(String taskName) {
        Map<String, Protos.TaskID> taskIdsByName = new HashMap<>();
        for (Collection<Protos.TaskInfo> pod : createdPods) {
            for (Protos.TaskInfo task : pod) {
                taskIdsByName.put(task.getName(), task.getTaskId());
            }
        }
        Protos.TaskID taskId = taskIdsByName.get(taskName);
        if (taskId == null) {
            throw new IllegalStateException(String.format(
                    "Unable to find task named %s, known tasks were: %s", taskName, taskIdsByName));
        }
        return taskId;
    }
}
