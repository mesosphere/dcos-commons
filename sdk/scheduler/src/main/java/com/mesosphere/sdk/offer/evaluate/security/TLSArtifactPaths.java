package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.specification.TransportEncryptionSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility for creating paths for {@link TLSArtifact} objects within a specified per-task context.
 */
public class TLSArtifactPaths {

    /**
     * Utility class for pairing paths related to a secret.
     */
    public static class Entry {
        /** Source location for a secret in the secret store. */
        public final String secretStorePath;
        /** Destination location for a secret on the task filesystem. */
        public final String mountPath;

        private Entry(String secretStorePath, String mountPath) {
            this.secretStorePath = secretStorePath;
            this.mountPath = mountPath;
        }
    }

    private static final Pattern KNOWN_SECRET_NAMES_PATTERN;
    static {
        Collection<String> possibleSecretNames = new ArrayList<>();
        for (TLSArtifact tlsArtifact : TLSArtifact.values()) {
            possibleSecretNames.add(tlsArtifact.getName());
        }
        KNOWN_SECRET_NAMES_PATTERN = Pattern.compile(String.format("^.+%s(?:%s)$",
                TLSArtifact.SECRET_STORE_NAME_DELIMITER,
                String.join("|", possibleSecretNames)));
    }

    private final String namespace;
    private final String taskInstanceName;
    private final String sansHash;

    public TLSArtifactPaths(String namespace, String taskInstanceName, String sansHash) {
        this.namespace = namespace;
        this.taskInstanceName = taskInstanceName;
        this.sansHash = sansHash;
    }

    public String getTaskSecretsNamespace() {
        return namespace;
    }

    public String getTaskInstanceName() {
        return taskInstanceName;
    }

    /**
     * Return a list of namespaced secret names (without namespace) for all known {@link TLSArtifact}s.
     */
    public Collection<String> getAllNames(String encryptionSpecName) {
        Collection<String> secretPaths = new ArrayList<>();
        for (TLSArtifact tlsArtifact : TLSArtifact.values()) {
            secretPaths.add(getSecretStoreName(tlsArtifact, encryptionSpecName));
        }
        return secretPaths;
    }

    /**
     * Returns a mapping of secret store path to mount path for all {@link TLSArtifact}s with the specified
     * {@link TransportEncryptionSpec.Type}.
     */
    public List<Entry> getPathsForType(TransportEncryptionSpec.Type type, String encryptionSpecName) {
        List<Entry> paths = new ArrayList<>();
        for (TLSArtifact tlsArtifact : TLSArtifact.values()) {
            if (tlsArtifact.getType().equals(type)) {
                paths.add(new Entry(
                        getSecretStorePath(tlsArtifact, encryptionSpecName),
                        tlsArtifact.getMountPath(encryptionSpecName)));
            }
        }
        return paths;
    }

    /**
     * Returns the appropriate namespaced secret store path for the provided {@link TLSArtifact}.
     */
    public String getSecretStorePath(TLSArtifact tlsArtifact, String encryptionSpecName) {
        return String.format("%s/%s", namespace, getSecretStoreName(tlsArtifact, encryptionSpecName));
    }

    /**
     * Filters the provided list of secret paths to just the ones which have a matching {@link TLSArtifact}
     * implementation.
     */
    public static Collection<String> getKnownTLSArtifacts(Collection<String> secretStorePaths) {
        return secretStorePaths.stream()
                .filter(secretStorePath -> KNOWN_SECRET_NAMES_PATTERN.matcher(secretStorePath).matches())
                .collect(Collectors.toList());
    }

    /**
     * Returns the appropriate name for the provided {@link TLSArtifact} to be used in a secret store.
     */
    private String getSecretStoreName(TLSArtifact tlsArtifact, String encryptionSpecName) {
        return tlsArtifact.getSecretStoreName(sansHash, taskInstanceName, encryptionSpecName);
    }
}
