package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.scheduler.SchedulerUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * This class contains Portworx-specific logic for handling volume name and volume's options.
 */
public class PortworxVolumeProvider implements ExternalVolumeProvider {

  private static final String SHARED_KEY = "shared";

  String volumeName;

  Map<String, String> driverOptions;

  public PortworxVolumeProvider(
      String serviceName,
      Optional<String> providedVolumeName,
      String podType,
      int podIndex,
      Map<String, String> driverOptions)
  {

    String volumeNameUnescaped;
    if (providedVolumeName.isPresent()) {
      volumeNameUnescaped = providedVolumeName.get();
    } else {
      volumeNameUnescaped = providedVolumeName.filter(name -> !name.isEmpty()).orElse(serviceName) + '-' + podType;
    }

    String volumeNameEscaped = SchedulerUtils.withEscapedSlashes(volumeNameUnescaped);

    if (driverOptions.containsKey(SHARED_KEY) && driverOptions.get(SHARED_KEY).equals("true")) {
      // If it is a shared volume, reset the volume name to
      // not have the pod index, so that all tasks get the
      // same volume
      this.volumeName = volumeNameEscaped;
    } else {
      this.volumeName = volumeNameEscaped + "-" + podIndex;
    }

    Map<String, String> options = new HashMap<>(driverOptions);
    // Favor creating volumes on the local node
    options.put("nodes", "LocalNode");

    this.driverOptions = options;
  }

  @Override
  public String getVolumeName() {
    return this.volumeName;
  }

  @Override
  public Map<String, String> getDriverOptions() {
    return this.driverOptions;
  }

}
