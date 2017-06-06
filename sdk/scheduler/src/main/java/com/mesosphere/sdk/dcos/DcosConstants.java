package com.mesosphere.sdk.dcos;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * This class encapsulates constants common to DC/OS and its services.
 */
public class DcosConstants {
    private static final String MASTER_MESOS = "master.mesos";

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public static final String MESOS_MASTER_ZK_CONNECTION_STRING = MASTER_MESOS + ":2181";
    public static final String MESOS_MASTER_URI = "http://" + MASTER_MESOS;
    public static final Boolean DEFAULT_GPU_POLICY = true;
    public static final String DEFAULT_OVERLAY_NETWORK = "dcos";
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings
    public static final Set<String> SUPPORTED_OVERLAY_NETWORKS = new HashSet<>(Arrays.asList(DEFAULT_OVERLAY_NETWORK));
    // DEFAULT_PORT_MAPPING_POLICY decides whether when joining an network that does not have an explicit
    // port-mapping capability to automatically map ports (ContainerIP:port : HostIP:port). I (arand) set this
    // default to true. This is a safer default because ports will remain as a Mesos resource for the task. Also,
    // in general if the network does not support port-mapping, but the task maps the ports the behavior is
    // as expected.
    public static final Boolean DEFAULT_PORT_MAPPING_POLICY = true;
    // These ports should be available to a container on the overlay regardless of it's permissions, it's unlikely
    // that a pod will ever exceed 1000 ports.
    public static final Integer OVERLAY_DYNAMIC_PORT_RANGE_START = 1025;
    public static final Integer OVERLAY_DYNAMIC_PORT_RANGE_END = 2025;

    /**
     * This must never change, as it affects the path to the SchemaVersion object for a given
     * framework name.
     *
     * @see com.mesosphere.sdk.state.DefaultSchemaVersionStore
     */
    public static final String SERVICE_ROOT_PATH_PREFIX = "/dcos-service-";

    public static boolean networkSupportsPortMapping(String networkName) {
        boolean supportsPortMapping;
        switch (networkName) {
            case DEFAULT_OVERLAY_NETWORK:
                supportsPortMapping = false;
                break;
            default:
                supportsPortMapping = DEFAULT_PORT_MAPPING_POLICY;
        }
        return  supportsPortMapping;
    }

    public static boolean isSupportedNetwork(String networkName) {
        return SUPPORTED_OVERLAY_NETWORKS.contains(networkName);
    }
}
