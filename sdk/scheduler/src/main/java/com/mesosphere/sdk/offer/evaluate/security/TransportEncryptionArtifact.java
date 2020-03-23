package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.specification.TransportEncryptionSpec;

/**
 * Interface for generating Transport related Artifacts.
 */

public interface TransportEncryptionArtifact {

  public TransportEncryptionSpec.Type getType();

  public String getName();

  public String getDescription();

  public String getMountPath(String transportEncryptionName);
}
