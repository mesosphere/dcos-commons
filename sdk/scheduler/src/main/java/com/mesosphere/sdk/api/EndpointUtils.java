package com.mesosphere.sdk.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.mesos.Protos.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
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
    /** TLD to be used for VIP-based hostnames. */
    private static final String VIP_HOST_TLD = "l4lb.thisdcos.directory";

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
     * Returns the correct Mesos DNS endpoint for the provided task and port running within the provided service.
     */
    public static String toMesosDnsEndpoint(String serviceName, String taskName, int port) {
        String hostname = String.format("%s.%s.mesos", taskName, toMesosDnsFolderedService(serviceName));
        return toEndpoint(hostname, port);
    }

    /**
     * Returns the correct L4LB VIP endpoint for the provided task and port running within the provided service.
     */
    public static String toVipEndpoint(String serviceName, VipInfo vipInfo) {
        String hostname = String.format("%s.%s.%s",
                removeSlashes(vipInfo.getVipName()), removeSlashes(serviceName), VIP_HOST_TLD);
        return toEndpoint(hostname, vipInfo.getVipPort());
    }

    /**
     * Returns the correct L4LB VIP hostname for accessing the Scheduler API given the provided service name.
     */
    public static String toSchedulerApiVipHostname(String serviceName) {
        return String.format("api.%s.marathon.%s", removeSlashes(serviceName), VIP_HOST_TLD);
    }

    /**
     * Returns a {@link Label} which can be included in the {@code org.apache.mesos.Protos.DiscoveryInfo} for a VIP.
     * This is the inverse of {@link #parseVipLabel(String, Label)}
     */
    public static Label createVipLabel(String vipName, int vipPort) {
        return Label.newBuilder()
                .setKey(String.format("%s%s", VIP_LABEL_PREFIX, UUID.randomUUID().toString()))
                .setValue(String.format("%s:%d", vipName, vipPort))
                .build();
    }

    /**
     * Extracts VIP information from the provided VIP label.
     * This is the inverse of {@link #createVipLabel(String, int)}.
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
    private static String removeSlashes(String serviceName) {
        return serviceName.replace("/", "");
    }

    /**
     * "/group1/group2/group3/group4/group5/kafka" => "kafka-group5-group4-group3-group2-group1.marathon.mesos".
     */
    private static String toMesosDnsFolderedService(String serviceName) {
        // Splitter returns an unmodifiable list:
        List<String> elems = new ArrayList<>();
        elems.addAll(Splitter.on('/').splitToList(serviceName));
        // Remove empty entry at start, which may have been added due to a leading slash:
        if (!elems.isEmpty() && elems.get(0).isEmpty()) {
            elems.remove(0);
        }
        Collections.reverse(elems);
        return Joiner.on('-').join(elems);
    }
}
