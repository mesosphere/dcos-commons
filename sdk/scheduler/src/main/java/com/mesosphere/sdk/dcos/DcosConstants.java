package com.mesosphere.sdk.dcos;

/**
 * This class encapsulates constants common to DC/OS and its services.
 */
public class DcosConstants {
    private static final String MASTER_MESOS = "master.mesos";

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public static final String MESOS_MASTER_ZK_CONNECTION_STRING = MASTER_MESOS + ":2181";
    public static final String MESOS_MASTER_URI = "http://" + MASTER_MESOS;
    public static final Boolean DEFAULT_GPU_POLICY = true;

    /**
     * This must never change, as it affects the path to the SchemaVersion object for a given
     * framework name.
     *
     * @see com.mesosphere.sdk.state.DefaultSchemaVersionStore
     */
    public static final String SERVICE_ROOT_PATH_PREFIX = "/dcos-service-";
}
