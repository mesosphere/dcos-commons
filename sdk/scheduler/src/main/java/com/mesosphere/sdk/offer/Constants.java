package com.mesosphere.sdk.offer;

/**
 * This class encapsulates constants of relevance to SDK Scheduler internals.
 *
 * @see com.mesosphere.sdk.offer.taskdata.EnvConstants
 * @see com.mesosphere.sdk.offer.taskdata.LabelConstants
 */
public class Constants {

    /** The name used for the deployment plan. */
    public static final String DEPLOY_PLAN_NAME = "deploy";
    /** The name used for the update plan. */
    public static final String UPDATE_PLAN_NAME = "update";

    /** The name used for reserved network port resources. */
    public static final String PORTS_RESOURCE_TYPE = "ports";
    /** The name used for reserved storage/disk resources. */
    public static final String DISK_RESOURCE_TYPE = "disk";

    /** The "any role" wildcard resource role. */
    public static final String ANY_ROLE = "*";

    /** The string prepended to reserved resources to indicate that they are uninstalled. */
    public static final String TOMBSTONE_MARKER = "uninstalled_";

    /** Prefix to use for VIP labels in DiscoveryInfos. */
    public static final String VIP_PREFIX = "VIP_";
    /** TLD to be used for VIP-based hostnames. */
    public static final String VIP_HOST_TLD = "l4lb.thisdcos.directory";
}
