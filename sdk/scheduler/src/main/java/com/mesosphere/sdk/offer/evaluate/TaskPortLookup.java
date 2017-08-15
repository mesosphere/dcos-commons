package com.mesosphere.sdk.offer.evaluate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.util.Strings;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mesosphere.sdk.offer.taskdata.EnvConstants;
import com.mesosphere.sdk.offer.taskdata.EnvUtils;
import com.mesosphere.sdk.specification.PortSpec;

/**
 * Searches TaskInfos for evidence of prior port assignments. Used for preserving/reusing previous dynamic ports.
 */
class TaskPortLookup {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskPortLookup.class);

    private final String taskName;
    private final Map<String, Long> lastTaskPorts;
    // TODO(nickbp): Remove this env storage after October 2017 when it's no longer used as a fallback for dynamic ports
    private final Map<String, String> lastTaskEnvs;

    TaskPortLookup(Protos.TaskInfo currentTask) {
        this.taskName = currentTask.getName();
        this.lastTaskPorts = new HashMap<>();
        for (Protos.Port port : currentTask.getDiscovery().getPorts().getPortsList()) {
            if (!Strings.isEmpty(port.getName())) {
                this.lastTaskPorts.put(port.getName(), (long) port.getNumber());
            }
        }
        this.lastTaskEnvs = EnvUtils.toMap(currentTask.getCommand().getEnvironment());
    }

    /**
     * Returns the last value used for the provided PortSpec. Mainly useful for dynamic ports whose value is determined
     * at time of launch.
     */
    Optional<Long> getPriorPort(PortSpec portSpec) {
        Long lastPort = lastTaskPorts.get(portSpec.getPortName());
        if (lastPort != null) {
            return Optional.of(lastPort);
        }

        // Fall back to searching the task environment.
        // Tasks launched in older SDK releases may omit the port names in the DiscoveryInfo.
        // TODO(nickbp): Remove this fallback after October 2017
        // When the PortSpec lacks an explicit env name, fall back to trying the legacy "PORT_<PORT_NAME>" default
        final String portEnvName = portSpec.getEnvKey() != null
                ? portSpec.getEnvKey()
                : EnvConstants.PORT_NAME_TASKENV_PREFIX + EnvUtils.toEnvName(portSpec.getPortName());
        try {
            return Optional.ofNullable(Long.parseLong(lastTaskEnvs.get(portEnvName)));
        } catch (NumberFormatException e) {
            // We're just making a best-effort attempt to recover the port value, so give up if this happens.
            LOGGER.warn(String.format(
                    "Failed to recover port %s from task %s environment variable %s",
                    portSpec.getPortName(), taskName, portEnvName), e);
        }

        // Port not found.
        return Optional.empty();
    }
}
