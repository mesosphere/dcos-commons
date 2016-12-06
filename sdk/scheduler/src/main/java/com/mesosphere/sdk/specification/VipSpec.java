package com.mesosphere.sdk.specification;

/**
 * Specification object for software defined VIPs.
 */
public interface VipSpec {
    /**
     * @return Application Port to which connections from VIP's port will be routed.
     */
    int getApplicationPort();

    /**
     * @return VIP's hostname.
     */
    String getVipName();

    /**
     * @return VIP's Port which will route connections to application Port.
     */
    int getVipPort();
}
