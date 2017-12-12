package com.mesosphere.sdk.offer.evaluate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.util.Strings;
import org.apache.mesos.Protos;

import com.mesosphere.sdk.specification.PortSpec;

/**
 * Searches TaskInfos for evidence of prior port assignments. Used for preserving/reusing previous dynamic ports.
 */
class TaskPortLookup {
    private final Map<String, Long> lastTaskPorts;

    TaskPortLookup(Protos.TaskInfo currentTask) {
        this.lastTaskPorts = new HashMap<>();
        for (Protos.Port port : currentTask.getDiscovery().getPorts().getPortsList()) {
            if (!Strings.isEmpty(port.getName())) {
                this.lastTaskPorts.put(port.getName(), (long) port.getNumber());
            }
        }
    }

    /**
     * Returns the last value used for the provided PortSpec. Mainly useful for dynamic ports whose value is determined
     * at time of launch.
     */
    Optional<Long> getPriorPort(PortSpec portSpec) {
        return Optional.ofNullable(lastTaskPorts.get(portSpec.getPortName()));
    }
}
