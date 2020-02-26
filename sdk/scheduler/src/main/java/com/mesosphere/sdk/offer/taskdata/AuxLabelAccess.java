package com.mesosphere.sdk.offer.taskdata;

import com.mesosphere.sdk.dcos.DcosConstants;
import com.mesosphere.sdk.http.EndpointUtils;
import com.mesosphere.sdk.http.EndpointUtils.VipInfo;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.specification.NamedVIPSpec;

import com.google.common.base.Splitter;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Label;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Utilities for adding labels to Mesos protobufs other than {@link Protos.TaskInfo}.
 * <p>
 * If you're accessing labels in {@link Protos.TaskInfo}s, you should be using {@link TaskLabelReader} and/or
 * {@link TaskLabelWriter}.
 */
public final class AuxLabelAccess {

  private static final Logger LOGGER = LoggingUtils.getLogger(AuxLabelAccess.class);

  private AuxLabelAccess() {
    // do not instantiate
  }

  // Reservation labels: Resource ID and Service Name

  /**
   * Assigns a resource ID label to the provided reservation.
   *
   * @param reservationBuilder the reservation where labels should be added
   * @param resourceId         a unique id for tracking the reservation
   */
  public static void setResourceId(
      Protos.Resource.ReservationInfo.Builder reservationBuilder,
      String resourceId)
  {
    Map<String, String> map = LabelUtils.toMap(reservationBuilder.getLabels());
    map.put(LabelConstants.RESOURCE_ID_RESERVATION_LABEL, resourceId);
    reservationBuilder.setLabels(LabelUtils.toProto(map));
  }

  /**
   * Returns the unique resource id which can be used for uniquely identifying this reservation, or an empty optional
   * if none is present. This label should always be present in reservations which were created by the SDK.
   */
  public static Optional<String> getResourceId(Protos.Resource.ReservationInfo reservation) {
    return getLabel(reservation.getLabels(), LabelConstants.RESOURCE_ID_RESERVATION_LABEL);
  }

  /**
   * Assigns a framework ID label to the provided reservation.
   *
   * @param reservationBuilder the reservation where labels should be added
   * @param frameworkId         a unique id representing the framework
   */
  public static void setFrameworkId(
      Protos.Resource.ReservationInfo.Builder reservationBuilder,
      String frameworkId)
  {
    Map<String, String> map = LabelUtils.toMap(reservationBuilder.getLabels());
    map.put(LabelConstants.FRAMEWORK_ID_RESERVATION_LABEL, frameworkId);
    reservationBuilder.setLabels(LabelUtils.toProto(map));
  }

  public static void setPortLabels(
          Protos.Port.Builder portBuilder,
          Map<String, String> portLabels) {
    portBuilder.setLabels(LabelUtils.toProto(portLabels));
  }

  /**
   * Returns the unique resource id which can be used for uniquely identifying this reservation, or an empty optional
   * if none is present. This label should always be present in reservations which were created by the SDK.
   */
  public static Optional<String> getFrameworkId(Protos.Resource.ReservationInfo reservation) {
    return getLabel(reservation.getLabels(), LabelConstants.FRAMEWORK_ID_RESERVATION_LABEL);
  }

  /**
   * Assigns a service namespace to the pvrovided reservation.
   *
   * @param reservationBuilder the reservation where labels should be added
   * @param namespace          a namespace to be assigned
   */
  public static void setResourceNamespace(
      Protos.Resource.ReservationInfo.Builder reservationBuilder, String namespace)
  {
    Map<String, String> map = LabelUtils.toMap(reservationBuilder.getLabels());
    map.put(LabelConstants.NAMESPACE_RESERVATION_LABEL, namespace);
    reservationBuilder.setLabels(LabelUtils.toProto(map));
  }

  /**
   * Returns the service name embedded in the reservation, or an empty optional if none is present. This label is only
   * present in reservations which were created in SDK 0.50 or later.
   */
  public static Optional<String> getResourceNamespace(Protos.Resource.ReservationInfo reservation) {
    return getLabel(reservation.getLabels(), LabelConstants.NAMESPACE_RESERVATION_LABEL);
  }

  public static void setDcosSpace(
      Protos.ExecutorInfo.Builder executorInfoBuilder,
      String dcosSpace)
  {
    // DC/OS Space
    executorInfoBuilder.setLabels(withLabel(
        executorInfoBuilder.getLabels(),
        LabelConstants.DCOS_SPACE_EXECUTORINFO_LABEL,
        dcosSpace));
  }

  public static void setNetworkLabels(
      Protos.NetworkInfo.Builder networkInfoBuilder,
      Map<String, String> labels)
  {
    // Network label passthrough
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
        String.format("%s%s", LabelConstants.VIP_LABEL_PREFIX, UUID.randomUUID().toString()),
        String.format("%s:%d", namedVIPSpec.getVipName(), namedVIPSpec.getVipPort()));

    if (!namedVIPSpec.getNetworkNames().isEmpty()) {
      // On named network
      boolean useHostIp = namedVIPSpec.getNetworkNames().stream()
          .filter(DcosConstants::networkSupportsPortMapping)
          .count() > 0;
      map.put(LabelConstants.VIP_OVERLAY_FLAG_KEY,
          useHostIp ? LabelConstants.VIP_BRIDGE_FLAG_VALUE : LabelConstants.VIP_OVERLAY_FLAG_VALUE);
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
   * @param label    a label from a {@code org.apache.mesos.Protos.DiscoveryInfo}
   * @return the VIP information or an empty Optional if the provided label is invalid or inapplicable
   */
  private static Optional<VipInfo> parseVipLabel(String taskName, Label label) {
    if (!label.getKey().startsWith(LabelConstants.VIP_LABEL_PREFIX)) {
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

  private static Optional<String> getLabel(Protos.Labels labels, String key) {
    if (labels.getLabelsCount() == 0) {
      // Shortcut/optimization
      return Optional.empty();
    }
    return Optional.ofNullable(LabelUtils.toMap(labels).get(key));
  }

  /**
   * Returns a copy of the provided {@link Protos.Labels} instance with the provided label added to the list.
   * If the provided label key already exists, it is updated with the new value.
   * <p>
   * This should only be used for custom label locations. If you're editing {@link Protos.TaskInfo} labels you should
   * use {@code TaskLabelWriter}.
   */
  private static Protos.Labels withLabel(Protos.Labels labels, String key, String value) {
    Map<String, String> map = LabelUtils.toMap(labels);
    map.put(key, value);
    return LabelUtils.toProto(map);
  }
}
