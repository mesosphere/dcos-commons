package com.mesosphere.sdk.offer.evaluate;

import java.util.Map;
import java.util.Optional;

/**
 * This class abstracts over creation different types of {@link ExternalVolumeProvider}.
 */
public final class ExternalVolumeProviderFactory {

  private ExternalVolumeProviderFactory() {
  }

  public static ExternalVolumeProvider getExternalVolumeProvider(
      String serviceName,
      Optional<String> volumeName,
      Optional<String> containerPath,
      String driverName,
      String podType,
      int podIndex,
      Map<String, String> driverOptions)
  {
    if ("pxd".equals(driverName)) {
      return new PortworxVolumeProvider(
          serviceName,
          volumeName,
          containerPath,
          podType,
          podIndex,
          driverOptions);
    } else {
      return new GenericDockerVolumeProvider(
          serviceName,
          volumeName,
          containerPath,
          podType,
          podIndex,
          driverOptions);
    }
  }
}
