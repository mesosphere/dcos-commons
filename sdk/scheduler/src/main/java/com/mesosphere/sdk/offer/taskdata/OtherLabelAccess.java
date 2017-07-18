package com.mesosphere.sdk.offer.taskdata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.mesosphere.sdk.api.EndpointUtils;
import com.mesosphere.sdk.api.EndpointUtils.VipInfo;
import com.mesosphere.sdk.specification.NamedVIPSpec;

/**
 * Utilities for adding labels to custom locations, other than {@link Protos.TaskInfo}s.
 *
 * If you're editing {@link Protos.TaskInfos}, you should be using {@link TaskLabelReader}/{@link TaskLabelWriter}.
 */
public class OtherLabelAccess {

    private static final Logger LOGGER = LoggerFactory.getLogger(OtherLabelAccess.class);

    /**
     * Label used in {@link Protos.Resource.ReservationInfo} to uniquely map tasks to reserved resources.
     */
    private static final String RESOURCE_ID_RESERVATION_LABEL = "resource_id";

    /**
     * Label used in {@link Protos.ExecutorInfo}s to indicate the "DC/OS Space"
     */
    private static final String DCOS_SPACE_EXECUTORINFO_LABEL = "DCOS_SPACE";

    /**
     * Prefix to use for VIP labels in {@link Protos.DiscoveryInfo}s.
     */
    private static final String VIP_LABEL_PREFIX = "VIP_";

    /**
     * Label key/value to be applied to {@link Protos.Port}s when an overlay network is configured.
     */
    @VisibleForTesting
    public static final String VIP_OVERLAY_FLAG_KEY = "network-scope";
    @VisibleForTesting
    public static final String VIP_OVERLAY_FLAG_VALUE = "container";

    private OtherLabelAccess() {
        // do not instantiate
    }

    public static void setResourceId(Protos.Resource.ReservationInfo.Builder reservationBuilder, String resourceId) {
        reservationBuilder.setLabels(
                withLabel(reservationBuilder.getLabels(), RESOURCE_ID_RESERVATION_LABEL, resourceId));
    }

    public static Optional<String> getResourceId(Protos.Resource.ReservationInfo reservation) {
        return Optional.ofNullable(LabelUtils.toMap(reservation.getLabels()).get(RESOURCE_ID_RESERVATION_LABEL));
    }

    public static void setDcosSpace(Protos.ExecutorInfo.Builder executorInfoBuilder, String dcosSpace) {
        executorInfoBuilder.setLabels(
                withLabel(executorInfoBuilder.getLabels(), DCOS_SPACE_EXECUTORINFO_LABEL, dcosSpace));
    }

    public static void setNetworkLabels(Protos.NetworkInfo.Builder networkInfoBuilder, Map<String, String> labels) {
        Map<String, String> map = LabelUtils.toMap(networkInfoBuilder.getLabels());
        map.putAll(labels);
        networkInfoBuilder.setLabels(LabelUtils.toProto(map));
    }

    /**
     * Updates the provided {@link Protos.Port} to contain the provided VIP information.
     * This is the inverse of {@link #getVIPsFromLabels(String, org.apache.mesos.Protos.Port)}.
     */
    public static void setVIPLabels(Protos.Port.Builder portBuilder, NamedVIPSpec namedVIPSpec) {
        Map<String, String> map = LabelUtils.toMap(portBuilder.getLabels());
        map.put(
                String.format("%s%s", VIP_LABEL_PREFIX, UUID.randomUUID().toString()),
                String.format("%s:%d", namedVIPSpec.getVipName(), namedVIPSpec.getVipPort()));
        if (!namedVIPSpec.getNetworkNames().isEmpty()) {
            // On named network
            map.put(VIP_OVERLAY_FLAG_KEY, VIP_OVERLAY_FLAG_VALUE);
        }
        portBuilder.setLabels(LabelUtils.toProto(map));
    }

    /**
     * Returns the VIP information, if any, within the provided {@link Protos.Port}.
     * This is the inverse of {@link #setVIPLabels(org.apache.mesos.Protos.Port.Builder, NamedVIPSpec)}.
     */
    public static Collection<VipInfo> getVIPsFromLabels(String taskName, Protos.Port port) {
        List<VipInfo> vips = new ArrayList<>();
        for (Label label : port.getLabels().getLabelsList()) {
            Optional<EndpointUtils.VipInfo> vipInfo = parseVipLabel(taskName, label);
            if (!vipInfo.isPresent()) {
                // Label doesn't appear to be for a VIP
                continue;
            }
            vips.add(vipInfo.get());
        }
        return vips;
    }

    /**
     * Extracts VIP information from the provided label, if VIP information is present.
     *
     * @param taskName task name for use in logs if there's a problem
     * @param label a label from a {@code org.apache.mesos.Protos.DiscoveryInfo}
     * @return the VIP information or an empty Optional if the provided label is invalid or inapplicable
     */
    private static Optional<VipInfo> parseVipLabel(String taskName, Label label) {
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
     * Returns a copy of the provided {@link Labels} instance with the provided label added to the list.
     * If the provided label key already exists, it is updated with the new value.
     *
     * This should only be used for custom label locations. If you're editing {@link TaskInfo} labels you should use
     * {@code TaskLabelWriter}.
     */
    private static Protos.Labels withLabel(Protos.Labels labels, String key, String value) {
        Map<String, String> map = LabelUtils.toMap(labels);
        map.put(key, value);
        return LabelUtils.toProto(map);
    }
}
