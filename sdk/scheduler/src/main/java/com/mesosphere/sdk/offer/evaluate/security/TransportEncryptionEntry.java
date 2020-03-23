package com.mesosphere.sdk.offer.evaluate.security;

/**
 * Utility class for pairing paths related to a secret.
 */
public final class TransportEncryptionEntry {
  /**
   * Source location for a secret in the secret store.
   */
  public final String secretStorePath;

  /**
   * Destination location for a secret on the task filesystem.
   */
  public final String mountPath;

  public TransportEncryptionEntry(String secretStorePath, String mountPath) {
    this.secretStorePath = secretStorePath;
    this.mountPath = mountPath;
  }
}
