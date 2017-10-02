package com.mesosphere.sdk.offer.evaluate.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.dcos.clients.CertificateAuthorityClient;
import com.mesosphere.sdk.dcos.clients.SecretsClient;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Automatically populates a secret service with missing TLS certificate content.
 */
public class TLSArtifactsUpdater {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String serviceName;
    private final SecretsClient secretsClient;
    private final TLSArtifactsGenerator tlsArtifactsGenerator;

    public TLSArtifactsUpdater(String serviceName, SecretsClient secretsClient, CertificateAuthorityClient caClient) {
        this(serviceName, secretsClient, new TLSArtifactsGenerator(caClient));
    }

    @VisibleForTesting
    TLSArtifactsUpdater(String serviceName, SecretsClient secretsClient, TLSArtifactsGenerator tlsArtifactsGenerator) {
        this.serviceName = serviceName;
        this.secretsClient = secretsClient;
        this.tlsArtifactsGenerator = tlsArtifactsGenerator;
    }

    /**
     * Checks if any TLS artifact secrets are missing, and writes them to the secret store if they are.
     */
    public void update(
            TLSArtifactPaths tlsArtifactPaths,
            CertificateNamesGenerator certificateNamesGenerator,
            String encryptionSpecName) throws Exception {
        String namespace = tlsArtifactPaths.getTaskSecretsNamespace();
        Collection<String> currentSecretNames = secretsClient.list(namespace);
        // Convert "namespace/secret" => "secret":
        Set<String> expectedSecretNames = new TreeSet<>(tlsArtifactPaths.getAllNames(encryptionSpecName));
        Set<String> missingSecrets = new TreeSet<>();
        missingSecrets.addAll(expectedSecretNames);
        missingSecrets.removeAll(currentSecretNames);
        if (missingSecrets.isEmpty()) {
            // Everything's present, nothing to do.
            logger.info("Task '{}' already has all {} expected secrets for TLS config '{}' in namespace '{}': {}",
                    tlsArtifactPaths.getTaskInstanceName(),
                    expectedSecretNames.size(),
                    encryptionSpecName,
                    namespace,
                    expectedSecretNames);
            return;
        }
        logger.info(
                "Task '{}' is missing {}/{} expected secrets for TLS config '{}' in namespace '{}': {} (current: {})",
                tlsArtifactPaths.getTaskInstanceName(),
                missingSecrets.size(),
                expectedSecretNames.size(),
                encryptionSpecName,
                namespace,
                missingSecrets,
                currentSecretNames);

        // Just in case, generate the new values BEFORE attempting to erase current values. This avoids a situation
        // where we delete old secrets, then fail to generate their replacements and leave everything in a bad state.
        Map<TLSArtifact, String> newArtifactValues = tlsArtifactsGenerator.generate(certificateNamesGenerator);

        // One or more secrets are missing. Erase any current values and start from scratch.
        for (String secretName : currentSecretNames.stream()
                .filter(path -> expectedSecretNames.contains(path))
                .collect(Collectors.toList())) {
            String secretPath = namespace + "/" + secretName;
            logger.info("Deleting secret: {}", secretPath);
            secretsClient.delete(secretPath);
        }

        // Generate and write new values after deleting any current values.
        for (Map.Entry<TLSArtifact, String> entry : newArtifactValues.entrySet()) {
            String secretStorePath = tlsArtifactPaths.getSecretStorePath(entry.getKey(), encryptionSpecName);
            logger.info("Creating new secret: {}", secretStorePath);
            secretsClient.create(secretStorePath,
                    new SecretsClient.Payload(serviceName, entry.getValue(), entry.getKey().getDescription()));
        }
    }
}
