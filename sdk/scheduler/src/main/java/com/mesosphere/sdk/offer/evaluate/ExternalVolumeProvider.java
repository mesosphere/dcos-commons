package com.mesosphere.sdk.offer.evaluate;

import java.util.Map;

interface ExternalVolumeProvider {
  String getVolumeName();

  Map<String, String> getDriverOptions();
}
