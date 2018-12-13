package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.specification.TransportEncryptionSpec;

import org.apache.commons.lang3.StringUtils;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The definition of a pregenerated TLS or Keystore secret.
 */
@SuppressWarnings("checkstyle:MultipleStringLiterals")
public enum TLSArtifact {

  // TLS secrets
  CERTIFICATE(TransportEncryptionSpec.Type.TLS, "certificate", "crt", "PEM encoded certificate"),
  PRIVATE_KEY(TransportEncryptionSpec.Type.TLS, "private-key", "key", "PEM encoded private key"),
  CA_CERTIFICATE(
      TransportEncryptionSpec.Type.TLS,
      "root-ca-certificate",
      "ca",
      "PEM encoded root CA certificate"
  ),

  // Keystore secrets
  KEYSTORE(
      TransportEncryptionSpec.Type.KEYSTORE,
      "keystore",
      "keystore",
      "Base64 encoded java keystore"
  ),
  TRUSTSTORE(
      TransportEncryptionSpec.Type.KEYSTORE,
      "truststore",
      "truststore",
      "Base64 encoded java trust store"
  );

  // Secrets service allows only limited set of characters in secret name. Here we're going to use the double
  // underscore as a partial name delimiter. The "/" can't be used here as task doesn't have access to secrets
  // nested to the current DCOS_SPACE.
  // Secret path allowed characters: {secretPath:[A-Za-z0-9-/_]+}
  // More info: https://docs.mesosphere.com/1.9/security/#serv-job
  static final String SECRET_STORE_NAME_DELIMITER = "__";

  private final TransportEncryptionSpec.Type type;

  private final String name;

  private final String extension;

  private final String description;

  TLSArtifact(
      TransportEncryptionSpec.Type type,
      String name,
      String extension,
      String description)
  {
    this.type = type;
    this.name = name;
    this.extension = extension;
    this.description = description;
  }

  public TransportEncryptionSpec.Type getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  /**
   * Returns the full name to be used for the secret in a secret store.
   */
  public String getSecretStoreName(
      String sansHash,
      String taskInstanceName,
      String transportEncryptionName)
  {
    String fullName = Stream.of(sansHash, taskInstanceName, transportEncryptionName, name)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.joining(SECRET_STORE_NAME_DELIMITER));
    if (type.equals(TransportEncryptionSpec.Type.KEYSTORE)) {
      // Include a prefix so that the secret will be decoded by the mesos secrets module. See: DCOS-17621
      fullName = String.format("__dcos_base64__%s", fullName);
    }
    return fullName;
  }

  /**
   * Returns the path location where the secret should be mounted into task filesystems.
   */
  public String getMountPath(String transportEncryptionName) {
    return String.format("%s.%s", transportEncryptionName, extension);
  }
}
