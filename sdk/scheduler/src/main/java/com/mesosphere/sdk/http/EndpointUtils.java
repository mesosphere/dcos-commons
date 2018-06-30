package com.mesosphere.sdk.http;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.scheduler.SchedulerConfig;

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
    public static String toAutoIpDomain(String serviceName, SchedulerConfig schedulerConfig) {
        // Unlike with VIPs and mesos-dns hostnames, dots are converted to dashes with autoip hostnames. See DCOS-16086.
        return String.format("%s.%s",
                removeSlashes(replaceDotsWithDashes(serviceName)),
                schedulerConfig.getServiceTLD());
    }

    /**
     * Returns the correct DNS hostname for the provided task running within the service.
     */
    public static String toAutoIpHostname(String serviceName, String taskName, SchedulerConfig schedulerConfig) {
        // Unlike with VIPs and mesos-dns hostnames, dots are converted to dashes with autoip hostnames. See DCOS-16086.
        return String.format("%s.%s", reverseSlashedSegmentsWithDashes(replaceDotsWithDashes(taskName)),
                toAutoIpDomain(serviceName, schedulerConfig));
    }

    /**
     * Returns the correct DNS hostname:port endpoint for the provided task and port running within the service.
     */
    public static String toAutoIpEndpoint(
            String serviceName, String taskName, int port, SchedulerConfig schedulerConfig) {
        return toEndpoint(toAutoIpHostname(serviceName, taskName, schedulerConfig), port);
    }

    /**
     * Returns the correct DNS hostname for accessing the Scheduler API given the provided service name.
     */
    public static String toSchedulerAutoIpHostname(String serviceName, SchedulerConfig schedulerConfig) {
        return toAutoIpHostname("marathon", serviceName, schedulerConfig);
    }

    /**
     * Returns the correct DNS hostname:port endpoint for accessing the Scheduler API given the provided service name.
     */
    public static String toSchedulerAutoIpEndpoint(String serviceName, SchedulerConfig schedulerConfig) {
        return toAutoIpEndpoint("marathon", serviceName, schedulerConfig.getApiServerPort(), schedulerConfig);
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

    /**
     * "/path/to/kafka" => "kafka-to-path".
     */
    private static String reverseSlashedSegmentsWithDashes(String name) {
        return Joiner.on('-').join(Lists.reverse(Splitter.on('/').omitEmptyStrings().splitToList(name)));
    }
}
