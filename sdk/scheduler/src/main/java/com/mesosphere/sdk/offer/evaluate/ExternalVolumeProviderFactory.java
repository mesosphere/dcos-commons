package com.mesosphere.sdk.offer.evaluate;

import java.util.Map;
import java.util.Optional;

public class ExternalVolumeProviderFactory {
    public static ExternalVolumeProvider getExternalVolumeProvider(String serviceName,
                                                                   Optional<String> volumeName,
                                                                   String driverName,
                                                                   int podIndex,
                                                                   Map<String, String> driverOptions) {

        if (driverName.equals("pxd")) {
            return new PortworxVolumeProvider(
                    serviceName,
                    volumeName,
                    driverName,
                    podIndex,
                    driverOptions);
        } else {
            throw new IllegalArgumentException("Unsupported external volume driver " + driverName + ".");
        }
    }
}
