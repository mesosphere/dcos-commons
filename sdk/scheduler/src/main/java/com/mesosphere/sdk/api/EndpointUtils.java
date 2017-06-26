package com.mesosphere.sdk.api;

import java.util.*;

import com.mesosphere.sdk.offer.Constants;
import org.apache.mesos.Protos.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;

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

        @VisibleForTesting
        VipInfo(String vipName, int vipPort) {
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

    /** Prefix to use for VIP labels in DiscoveryInfos. */
    private static final String VIP_LABEL_PREFIX = "VIP_";

    private static final Logger LOGGER = LoggerFactory.getLogger(EndpointUtils.class);

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
     * Returns the correct L4LB VIP endpoint for the provided task and port running within the provided service.
     */
    public static String toVipEndpoint(String serviceName, VipInfo vipInfo) {
        String hostname = String.format("%s.%s.%s",
                removeSlashes(vipInfo.getVipName()),
                removeSlashes(serviceName),
                Constants.VIP_HOST_TLD);
        return toEndpoint(hostname, vipInfo.getVipPort());
    }

    /**
     * Returns the correct L4LB VIP hostname for accessing the Scheduler API given the provided service name.
     */
    public static String toSchedulerApiVipHostname(String serviceName) {
        return String.format("api.%s.marathon.%s", removeSlashes(serviceName), Constants.VIP_HOST_TLD);
    }

    /**
     * Returns a collection of {@link Label} which can be included in the {@code org.apache.mesos.Protos.DiscoveryInfo}
     * for a VIP. This is the inverse of {@link #parseVipLabel(String, Label)}
     */
    public static Collection<Label> createVipLabels(String vipName, long vipPort, boolean onNamedNetwork) {
        List<Label> labels = new ArrayList<>();
        labels.add(Label.newBuilder()
                .setKey(String.format("%s%s", Constants.VIP_PREFIX, UUID.randomUUID().toString()))
                .setValue(String.format("%s:%d", vipName, vipPort))
                .build());
        if (onNamedNetwork) {
            labels.add(Label.newBuilder()
                    .setKey(String.format("%s", Constants.VIP_OVERLAY_FLAG_KEY))
                    .setValue(String.format("%s", Constants.VIP_OVERLAY_FLAG_VALUE))
                    .build());
        }
        return labels;

    }

    /**
     * Extracts VIP information from the provided VIP label.
     * This is the inverse of {@link #createVipLabels(String, long, boolean)}.
     *
     * @param taskName task name for use in logs if there's a problem
     * @param label a label from a {@code org.apache.mesos.Protos.DiscoveryInfo}
     * @return the VIP information or an empty Optional if the provided label is invalid or inapplicable
     */
    public static Optional<VipInfo> parseVipLabel(String taskName, Label label) {
        if (!label.getKey().startsWith(VIP_LABEL_PREFIX)) {
            return Optional.empty();
        }

        // Expected VIP label format: "<vipname>:<port>"
        List<String> namePort = Splitter.on(':').splitToList(label.getValue());
        if (namePort.size() != 2) {
            LOGGER.error("Task {}'s VIP value for {} is invalid, expected 2 components but got {}: {}",
                    taskName, label.getKey(), namePort.size(), label.getValue());
            return Optional.empty();
        }
        int vipPort;
        try {
            vipPort = Integer.parseInt(namePort.get(1));
        } catch (NumberFormatException e) {
            LOGGER.error(String.format(
                    "Unable to Task %s's VIP port from %s as an int",
                    taskName, label.getValue()), e);
            return Optional.empty();
        }
        return Optional.of(new VipInfo(namePort.get(0), vipPort));
    }

    /**
     * "/group1/group2/group3/group4/group5/kafka" => "group1group2group3group4group5kafka".
     */
    private static String removeSlashes(String name) {
        return name.replace("/", "");
    }

    /**
     * "hello.kafka" => "hello-kafka". Used for values in autoip hostnames. Unlike with VIPs and mesos-dns hostnames,
     * dots are converted to dashes with autoip hostnames. See DCOS-16086.
     */
    private static String replaceDotsWithDashes(String name) {
        return name.replace('.', '-');
    }
}
