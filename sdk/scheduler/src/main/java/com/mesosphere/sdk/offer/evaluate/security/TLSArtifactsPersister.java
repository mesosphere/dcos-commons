package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.dcos.SecretsClient;
import com.mesosphere.sdk.dcos.secrets.Secret;
import com.mesosphere.sdk.dcos.secrets.SecretsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Stores TLSArtifacts to a secret service.
 */
public class TLSArtifactsPersister {

    private SecretsClient secretsClient;
    private String serviceName;

    private Logger logger = LoggerFactory.getLogger(getClass());

    public TLSArtifactsPersister(SecretsClient secretsClient, String serviceName) {
        this.secretsClient = secretsClient;
        this.serviceName = serviceName;
    }

    /**
     * Store TLSArtifacts set to secret service.
     *
     * This method expects the namespace to be empty and no secret is existing. To clean up the namespace
     * use {@link TLSArtifactsPersister ::cleanUpSecrets} call.
     * @param tlsArtifacts
     */
    public void persist(
            SecretNameGenerator secretNameGenerator,
            TLSArtifacts tlsArtifacts)
            throws IOException, SecretsException, CertificateException, NoSuchAlgorithmException, KeyStoreException {
        logger.info(String.format("Creating new secret: %s", secretNameGenerator.getCertificatePath()));
        secretsClient.create(
                secretNameGenerator.getCertificatePath(),
                buildSecret(tlsArtifacts.getCertPEM(), "PEM encoded certificate"));

        logger.info(String.format("Creating new secret: %s", secretNameGenerator.getPrivateKeyPath()));
        secretsClient.create(
                secretNameGenerator.getPrivateKeyPath(),
                buildSecret(tlsArtifacts.getPrivateKeyPEM(), "PEM encoded private key"));

        logger.info(String.format("Creating new secret: %s", secretNameGenerator.getRootCACertPath()));
        secretsClient.create(
                secretNameGenerator.getRootCACertPath(),
                buildSecret(tlsArtifacts.getRootCACertPEM(), "PEM encoded root CA certificate"));

        logger.info(String.format("Creating new secret: %s", secretNameGenerator.getKeyStorePath()));
        secretsClient.create(
                secretNameGenerator.getKeyStorePath(),
                buildSecret(
                        serializeKeyStoreToBase64(
                                tlsArtifacts.getKeyStore()), "Base64 encoded java keystore"));

        logger.info(String.format("Creating new secret: %s", secretNameGenerator.getTrustStorePath()));
        secretsClient.create(
                secretNameGenerator.getTrustStorePath(),
                buildSecret(
                        serializeKeyStoreToBase64(
                                tlsArtifacts.getTrustStore()), "Base64 encoded java trust store"));
    }

    /**
     * Returns true if all expected paths are stored in secrets service.
     *
     * The method doesn't check content of stored artifacts.
     * @return
     */
    public boolean isArtifactComplete(
            SecretNameGenerator secretNameGenerator) throws IOException, SecretsException {
        String namespace = secretNameGenerator.getTaskSecretsNamespace();
        Collection<String> existing = secretsClient.list(namespace);

        int namespaceLength = namespace.length();
        Collection<String> toProvision = secretNameGenerator
                .getAllSecretPaths()
                .stream()
                .map(secret -> secret.substring(namespaceLength + 1))
                .collect(Collectors.toList());

        toProvision.removeAll(existing);
        return toProvision.size() == 0;
    }

    /**
     * Removes all existing secrets stored under given namespace.
     * @param secretNameGenerator
     * @throws IOException
     * @throws SecretsException
     */
    public void cleanUpSecrets(
            SecretNameGenerator secretNameGenerator) throws IOException, SecretsException {
        String namespace = secretNameGenerator.getTaskSecretsNamespace();
        List<String> toDelete = secretNameGenerator
                .getAllSecretPaths()
                .stream()
                .map(path -> path.substring(namespace.length() + 1))
                .collect(Collectors.toList());

        List<String> existing = secretsClient.list(namespace)
                .stream()
                .filter(path -> toDelete.contains(path))
                .collect(Collectors.toList());

        for (String secretName : existing) {
            String secretPath = namespace + "/" + secretName;
            logger.info(String.format("Deleting secret '%s'", secretPath));
            secretsClient.delete(secretPath);
        }
    }

    private Secret buildSecret(String value, String description) {
        return new Secret.Builder()
                .value(value)
                .author(serviceName)
                .description(description)
                .build();
    }

    private String serializeKeyStoreToBase64(
            KeyStore keyStore) throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        ByteArrayOutputStream keyStoreOs = new ByteArrayOutputStream();
        keyStore.store(keyStoreOs, TLSArtifacts.getKeystorePassword());
        return Base64.getEncoder().encodeToString(keyStoreOs.toByteArray());
    }

}
