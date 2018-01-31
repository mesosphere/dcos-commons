package com.mesosphere.sdk.dcos;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class encapsulates constants common to DC/OS and its services.
 */
public class DcosConstants {

    private static final Logger LOGGER = LoggerFactory.getLogger(DcosConstants.class);

    private static final String MESOS_MASTER = "master.mesos";
    private static final String MESOS_LEADER = "leader.mesos";

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public static final String MESOS_MASTER_ZK_CONNECTION_STRING = MESOS_MASTER + ":2181";
    public static final String MESOS_LEADER_URI = "http://" + MESOS_LEADER;
    public static final String MESOS_MASTER_URI = "http://" + MESOS_MASTER;
    public static final String DEFAULT_SECRET_STORE_URI = MESOS_MASTER_URI + "/secrets/v1/secret/default/";
    public static final String CA_BASE_URI = MESOS_MASTER_URI + "/ca/api/v2/";
    public static final String IAM_AUTH_URL = MESOS_MASTER_URI + "/acs/api/v1/auth/login";
    public static final boolean DEFAULT_GPU_POLICY = false;
    public static final String DEFAULT_IP_PROTOCOL = "tcp";

    // These ports should be available to a container on the overlay regardless of it's permissions, it's unlikely
    // that a pod will ever exceed 1000 ports.
    public static final Integer OVERLAY_DYNAMIC_PORT_RANGE_START = 1025;
    public static final Integer OVERLAY_DYNAMIC_PORT_RANGE_END = 2025;

    public static final String DEFAULT_SERVICE_USER = "root";

    // Network Names

    private static final String DEFAULT_OVERLAY_NETWORK = "dcos";
    private static final String DEFAULT_BRIDGE_NETWORK = "mesos-bridge";
    private static final Set<String> SUPPORTED_OVERLAY_NETWORKS = new HashSet<>(
            Arrays.asList(DEFAULT_OVERLAY_NETWORK, DEFAULT_BRIDGE_NETWORK));

    public static boolean networkSupportsPortMapping(String networkName) {
        if (networkName.equals(DEFAULT_BRIDGE_NETWORK)) {
            return true;
        } else if (networkName.equals(DEFAULT_OVERLAY_NETWORK)) {
            return false;
        } else {
            // Here we decide whether to automatically map ports (ContainerIP:port : HostIP:port) when joining a network
            // that does not have an explicit port-mapping capability. It seems that port mapping (bridge networking) is
            // in fact not common so we default to disabled.
            return false;
        }
    }

    public static void warnIfUnsupportedNetwork(String networkName) {
        if (!SUPPORTED_OVERLAY_NETWORKS.contains(networkName)) {
            LOGGER.warn(
                    "Virtual network '{}' is not supported, unexpected behavior may result (expected one of: {})",
                    networkName, SUPPORTED_OVERLAY_NETWORKS);
        }
    }
}
