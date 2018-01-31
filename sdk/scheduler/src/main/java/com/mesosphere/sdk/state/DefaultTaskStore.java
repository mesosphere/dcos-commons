package com.mesosphere.sdk.state;

import java.util.Optional;

import org.apache.mesos.Protos;

import com.google.common.annotations.VisibleForTesting;

/**
 * Implementation of the developer-facing {@link TaskStore} interface, which is effectively an abstract, minimal subset
 * of {@link StateStore}.
 */
public class DefaultTaskStore implements TaskStore {

    private final StateStore stateStore;

    public DefaultTaskStore(StateStore stateStore) {
        this.stateStore = stateStore;
    }

    @VisibleForTesting
    public StateStore getStateStore() {
        return stateStore;
    }

    @Override
    public Optional<String> getTaskIp(String taskName) {
        Optional<Protos.TaskStatus> taskStatus = StateStoreUtils.getTaskStatusFromProperty(stateStore, taskName);
        if (!taskStatus.isPresent()) {
            return Optional.empty();
        }
        if (taskStatus.get().getContainerStatus().getNetworkInfosCount() == 0) {
            return Optional.empty();
        }
        Protos.NetworkInfo networkInfo = taskStatus.get().getContainerStatus().getNetworkInfos(0);
        if (networkInfo.getIpAddressesCount() == 0) {
            return Optional.empty();
        }
        return Optional.of(networkInfo.getIpAddresses(0).getIpAddress());
    }
}
