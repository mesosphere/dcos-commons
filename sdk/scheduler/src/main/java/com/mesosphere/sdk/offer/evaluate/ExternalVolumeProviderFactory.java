package com.mesosphere.sdk.offer.evaluate;

import java.util.Map;
import java.util.Optional;

/**
 * This class abstracts over creation different types of {@link ExternalVolumeProvider}.
 */
public final class ExternalVolumeProviderFactory {

  private ExternalVolumeProviderFactory() {
  }

  public static ExternalVolumeProvider getExternalVolumeProvider(String serviceName,
                                                                 Optional<String> volumeName,
                                                                 String driverName,
                                                                 int podIndex,
                                                                 Map<String, String> driverOptions)
  {

    if ("pxd".equals(driverName)) {
      return new PortworxVolumeProvider(
          serviceName,
          volumeName,
          podIndex,
          driverOptions);
    } else if ("netapp".equals(driverName)) {
      return new NetAppVolumeProvider(
          serviceName,
          volumeName,
          podIndex,
          driverOptions);
    } else {
      throw new IllegalArgumentException("Unsupported external volume driver " + driverName);
    }
  }
}
