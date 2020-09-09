package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.scheduler.SchedulerUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * This class contains NetApp-specific logic for handling volume name and volume's options.
 */
public class NetAppVolumeProvider implements ExternalVolumeProvider {

  String volumeName;

  Map<String, String> driverOptions;

  public NetAppVolumeProvider(
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
      volumeNameUnescaped = providedVolumeName.filter(name -> !name.isEmpty()).orElse(serviceName) + '_' + podType;
    }

    String volumeNameEscaped = SchedulerUtils.withEscapedSlashes(volumeNameUnescaped);
    this.volumeName = volumeNameEscaped + "_" + podIndex;

    Map<String, String> options = new HashMap<>(driverOptions);
    options.put("name", this.volumeName);

    this.driverOptions = options;
  }

  @Override
  public String getVolumeName() {
    return volumeName;
  }

  @Override
  public Map<String, String> getDriverOptions() {
    return driverOptions;
  }

}
