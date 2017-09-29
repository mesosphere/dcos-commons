package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.specification.TransportEncryptionSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility for creating paths for {@link Secret} objects within a specified per-task context.
 */
public class SecretStorePaths {

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
        for (Secret secret : Secret.values()) {
            possibleSecretNames.add(secret.getName());
        }
        KNOWN_SECRET_NAMES_PATTERN = Pattern.compile(String.format("^.+%s(?:%s)$",
                Secret.SECRET_STORE_PATH_DELIMITER,
                String.join("|", possibleSecretNames)));
    }

    private final String namespace;
    private final String taskInstanceName;
    private final String sansHash;

    public SecretStorePaths(String namespace, String taskInstanceName, String sansHash) {
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
     * Return a list of namespaced secret names (without namespace) for all known {@link Secret}s.
     */
    public Collection<String> getAllNames(String encryptionSpecName) {
        Collection<String> secretPaths = new ArrayList<>();
        for (Secret secret : Secret.values()) {
            secretPaths.add(getSecretStoreName(secret, encryptionSpecName));
        }
        return secretPaths;
    }

    /**
     * Returns a mapping of secret store path to mount path for all {@link Secret}s with the specified
     * {@link TransportEncryptionSpec.Type}.
     */
    public List<Entry> getPathsForType(TransportEncryptionSpec.Type type, String encryptionSpecName) {
        List<Entry> paths = new ArrayList<>();
        for (Secret secret : Secret.values()) {
            if (secret.getType().equals(type)) {
                paths.add(new Entry(
                        getSecretStorePath(secret, encryptionSpecName),
                        secret.getMountPath(encryptionSpecName)));
            }
        }
        return paths;
    }

    /**
     * Returns the appropriate namespaced secret store path for the provided {@link Secret}.
     */
    public String getSecretStorePath(Secret secret, String encryptionSpecName) {
        return String.format("%s/%s", namespace, getSecretStoreName(secret, encryptionSpecName));
    }

    /**
     * Filters the provided list of secret paths to just the ones which have a matching {@link Secret} implementation.
     */
    public static Collection<String> getKnownSecrets(Collection<String> secretStorePaths) {
        return secretStorePaths.stream()
                .filter(secretStorePath -> KNOWN_SECRET_NAMES_PATTERN.matcher(secretStorePath).matches())
                .collect(Collectors.toList());
    }

    /**
     * Returns the appropriate name for the provided {@link Secret} to be used in a secret store.
     */
    private String getSecretStoreName(Secret secret, String encryptionSpecName) {
        return secret.getSecretStoreName(sansHash, taskInstanceName, encryptionSpecName);
    }
}
