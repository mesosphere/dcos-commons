package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.specification.TransportEncryptionSpec;

import java.util.Collection;
import java.util.List;

/**
 *  Interface for creating paths for {@TransportEncryptionArtifact} objects within a specified per-task context.
 */
public interface TransportEncryptionArtifactPaths {

  public String getTaskSecretsNamespace();

  public String getTaskInstanceName();

  public Collection<String> getAllNames(String encryptionSpecName);

  public List<TransportEncryptionEntry> getPathsForType(TransportEncryptionSpec.Type type, String encryptionSpecName);
}
