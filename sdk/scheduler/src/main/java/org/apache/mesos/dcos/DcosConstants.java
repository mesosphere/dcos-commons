package org.apache.mesos.dcos;

import org.apache.mesos.curator.CuratorSchemaVersionStore;

/**
 * This class encapsulates constants common to DC/OS and its services.
 */
public class DcosConstants {
    private static final String MASTER_MESOS = "master.mesos";

    @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
    public static final String MESOS_MASTER_ZK_CONNECTION_STRING = MASTER_MESOS + ":2181";
    public static final String MESOS_MASTER_URI = "http://" + MASTER_MESOS;

    /**
     * This must never change, as it affects the path to the SchemaVersion object for a given
     * framework name.
     *
     * @see CuratorSchemaVersionStore
     */
    public static final String SERVICE_ROOT_PATH_PREFIX = "/dcos-service-";
}
