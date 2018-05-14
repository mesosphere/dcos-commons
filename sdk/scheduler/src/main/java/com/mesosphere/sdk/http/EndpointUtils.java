package com.mesosphere.sdk.http;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.SchedulerConfig;

/**
 * Utilities relating to the creation and interpretation of endpoints between DC/OS tasks.
 */
public class EndpointUtils {
    private static final Object lock = new Object();
    private static EndpointUtils endpointUtils;


    public static EndpointUtils getInstance() {
        synchronized (lock) {
            if (endpointUtils == null) {
                endpointUtils = new EndpointUtils();
            }
            return endpointUtils;
        }
    }

    public static void overrideEndpointUtils(EndpointUtils overrides) {
        synchronized (lock) {
            endpointUtils = overrides;
        }
    }

    /**
     * Simple data container representing information about a task VIP entry.
     */
    public static class VipInfo {
        private final String vipName;
        private final int vipPort;

        public VipInfo(String vipName, int vipPort) {
            this.vipName = vipName;
            this.vipPort = vipPort;
        }

        public String getVipName() {
            return vipName;
        }

        public int getVipPort() {
            return vipPort;
        }
    }

    protected EndpointUtils() {
        // use getInstance()
    }

    /**
     * Concatenates the provided hostname/port in "hostname:port" format.
     */
    public String toEndpoint(String hostname, int port) {
        return String.format("%s:%d", hostname, port);
    }

    /**
     * Returns the correct DNS domain for tasks within the service.
     */
    public String toAutoIpDomain(String serviceName, SchedulerConfig schedulerConfig) {
        // Unlike with VIPs and mesos-dns hostnames, dots are converted to dashes with autoip hostnames. See DCOS-16086.
        return String.format("%s.%s",
                removeSlashes(replaceDotsWithDashes(serviceName)),
                schedulerConfig.getServiceTLD());
    }

    /**
     * Returns the correct DNS hostname for the provided task running within the service.
     */
    public String toAutoIpHostname(String serviceName, String taskName, SchedulerConfig schedulerConfig) {
        // Unlike with VIPs and mesos-dns hostnames, dots are converted to dashes with autoip hostnames. See DCOS-16086.
        return String.format("%s.%s", removeSlashes(replaceDotsWithDashes(taskName)),
                toAutoIpDomain(serviceName, schedulerConfig));
    }

    /**
     * Returns the correct DNS hostname:port endpoint for the provided task and port running within the service.
     */
    public String toAutoIpEndpoint(String serviceName,
                                          String taskName,
                                          int port,
                                          SchedulerConfig schedulerConfig) {
        return toEndpoint(toAutoIpHostname(serviceName, taskName, schedulerConfig), port);
    }

    /**
     * Returns the correct DNS domain for VIPs within the service.
     */
    public String toVipDomain(String serviceName) {
        return String.format("%s.%s", removeSlashes(serviceName), Constants.VIP_HOST_TLD);
    }

    /**
     * Returns the correct L4LB VIP hostname for the provided task running within the provided service.
     */
    public String toVipHostname(String serviceName, VipInfo vipInfo) {
        return String.format("%s.%s", removeSlashes(vipInfo.getVipName()), toVipDomain(serviceName));
    }

    /**
     * Returns the correct L4LB VIP endpoint for the provided task and port running within the provided service.
     */
    public String toVipEndpoint(String serviceName, VipInfo vipInfo) {
        return toEndpoint(toVipHostname(serviceName, vipInfo), vipInfo.getVipPort());
    }

    /**
     * Returns the correct L4LB VIP hostname for accessing the Scheduler API given the provided service name.
     */
    public String toSchedulerApiVipHostname(String serviceName) {
        return String.format("api.%s.marathon.%s", removeSlashes(serviceName), Constants.VIP_HOST_TLD);
    }

    /**
     * "/group1/group2/group3/group4/group5/kafka" => "group1group2group3group4group5kafka".
     */
    public String removeSlashes(String name) {
        return name.replace("/", "");
    }

    /**
     * "hello.kafka" => "hello-kafka". Used for values in autoip hostnames. Unlike with VIPs and mesos-dns hostnames,
     * dots are converted to dashes with autoip hostnames. See DCOS-16086.
     */
    public String replaceDotsWithDashes(String name) {
        return name.replace('.', '-');
    }
}
