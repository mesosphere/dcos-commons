package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.specification.DefaultPortSpec;
import org.apache.mesos.Protos.DiscoveryInfo;

import java.time.Duration;

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
    /** The name used for cpu resources. */
    public static final String CPUS_RESOURCE_TYPE = "cpus";
    /** The name used for memory resources. */
    public static final String MEMORY_RESOURCE_TYPE = "mem";


    /** The "any role" wildcard resource role. */
    public static final String ANY_ROLE = "*";

    /** The string prepended to reserved resources to indicate that they are uninstalled. */
    public static final String TOMBSTONE_MARKER = "uninstalled_";

    /** TLD to be used for VIP-based hostnames. */
    public static final String VIP_HOST_TLD = "l4lb.thisdcos.directory";

    /**
     * TLD for navstar-based DNS. Resolves to the IP of the host iff the container if on the host network and the IP of
     * the container iff the container is on the overlay network. If the container is on multiple virtual networks or
     * experimenting with different DNS providers this TLD may have unexpected behavior.
     */
    public static final String DNS_TLD = "autoip.dcos.thisdcos.directory";

    /**
     * The visibility setting to use by default in Mesos Ports, for VIP ports. Non-VIP ports are currently hidden by
     * default.
     *
     * This may be overridden by manually constructing the {@link com.mesosphere.sdk.specification.NamedVIPSpec} or
     * {@link DefaultPortSpec}.
     *
     * As of this writing, this setting is only used by {@link com.mesosphere.sdk.api.EndpointsResource} for determining
     * what ports to advertise, where {@code EXTERNAL} means advertise and non-{@code EXTERNAL} means don't advertise.
     * According to the networking team this isn't currently used by DC/OS itself (as of 1.10).
     */
    public static final DiscoveryInfo.Visibility DISPLAYED_PORT_VISIBILITY = DiscoveryInfo.Visibility.EXTERNAL;

    /**
     * The visibility setting to use by default in Mesos Ports, for non-VIP ports. This may be revisited later where
     * they will be made visible by default.
     */
    public static final DiscoveryInfo.Visibility OMITTED_PORT_VISIBILITY = DiscoveryInfo.Visibility.CLUSTER;

    /**
     * The visibility setting to use by default in a Task's DiscoveryInfo, for both VIP and non-VIP ports.
     *
     * According to the networking team this isn't currently used by DC/OS itself (as of 1.10). It likewise isn't used
     * by the SDK.
     */
    public static final DiscoveryInfo.Visibility DEFAULT_TASK_DISCOVERY_VISIBILITY = DiscoveryInfo.Visibility.CLUSTER;

    /**
     * The duration in seconds to decline offers the scheduler does not need for the foreseeable future.
     */
    public static final int LONG_DECLINE_SECONDS = Math.toIntExact(Duration.ofDays(14).getSeconds());

    /**
     * The duration in seconds to decline offers the scheduler does not need for a short time.
     */
    public static final int SHORT_DECLINE_SECONDS = 5;
}
