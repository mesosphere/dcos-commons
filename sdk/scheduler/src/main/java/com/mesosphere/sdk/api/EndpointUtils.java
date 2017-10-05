package com.mesosphere.sdk.api;

import com.mesosphere.sdk.offer.Constants;

/**
 * Utilities relating to the creation and interpretation of endpoints between DC/OS tasks.
 */
public class EndpointUtils {

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

    private EndpointUtils() {
        // do not instantiate
    }

    /**
     * Concatenates the provided hostname/port in "hostname:port" format.
     */
    public static String toEndpoint(String hostname, int port) {
        return String.format("%s:%d", hostname, port);
    }

    /**
     * Returns the correct DNS domain for tasks within the service.
     */
    public static String toAutoIpDomain(String serviceName) {
        // Unlike with VIPs and mesos-dns hostnames, dots are converted to dashes with autoip hostnames. See DCOS-16086.
        return String.format("%s.%s", removeSlashes(replaceDotsWithDashes(serviceName)), Constants.DNS_TLD);
    }

    /**
     * Returns the correct DNS hostname for the provided task running within the service.
     */
    public static String toAutoIpHostname(String serviceName, String taskName) {
        // Unlike with VIPs and mesos-dns hostnames, dots are converted to dashes with autoip hostnames. See DCOS-16086.
        return String.format("%s.%s", removeSlashes(replaceDotsWithDashes(taskName)), toAutoIpDomain(serviceName));
    }

    /**
     * Returns the correct DNS hostname:port endpoint for the provided task and port running within the service.
     */
    public static String toAutoIpEndpoint(String serviceName, String taskName, int port) {
        return toEndpoint(toAutoIpHostname(serviceName, taskName), port);
    }

    /**
     * Returns the correct DNS domain for VIPs within the service.
     */
    public static String toVipDomain(String serviceName) {
        return String.format("%s.%s", removeSlashes(serviceName), Constants.VIP_HOST_TLD);
    }

    /**
     * Returns the correct L4LB VIP hostname for the provided task running within the provided service.
     */
    public static String toVipHostname(String serviceName, VipInfo vipInfo) {
        return String.format("%s.%s", removeSlashes(vipInfo.getVipName()), toVipDomain(serviceName));
    }

    /**
     * Returns the correct L4LB VIP endpoint for the provided task and port running within the provided service.
     */
    public static String toVipEndpoint(String serviceName, VipInfo vipInfo) {
        return toEndpoint(toVipHostname(serviceName, vipInfo), vipInfo.getVipPort());
    }

    /**
     * Returns the correct L4LB VIP hostname for accessing the Scheduler API given the provided service name.
     */
    public static String toSchedulerApiVipHostname(String serviceName) {
        return String.format("api.%s.marathon.%s", removeSlashes(serviceName), Constants.VIP_HOST_TLD);
    }

    /**
     * "/group1/group2/group3/group4/group5/kafka" => "group1group2group3group4group5kafka".
     */
    public static String removeSlashes(String name) {
        return name.replace("/", "");
    }

    /**
     * "hello.kafka" => "hello-kafka". Used for values in autoip hostnames. Unlike with VIPs and mesos-dns hostnames,
     * dots are converted to dashes with autoip hostnames. See DCOS-16086.
     */
    public static String replaceDotsWithDashes(String name) {
        return name.replace('.', '-');
    }
}
