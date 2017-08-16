package com.mesosphere.sdk.offer.evaluate.security;

import com.mesosphere.sdk.scheduler.SchedulerFlags;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Provides a way to generate paths for secrets storing a private key, a certificate and a keystore.
 */
public class SecretNameGenerator {

    private final String taskInstanceName;
    private final String namespace;
    private final String transportEncryptionName;
    private final String sanHash;

    public static final String SECRET_NAME_CERTIFICATE = "certificate";
    public static final String SECRET_NAME_PRIVATE_KEY = "private-key";
    public static final String SECRET_NAME_CA_CERT = "root-ca-certificate";
    public static final String SECRET_NAME_KEYSTORE = "keystore";
    public static final String SECRET_NAME_TRUSTSTORE = "truststore";

    // Secrets service allows only limited set of characters in secret name. Here we're going to use the double
    // underscore as a partial name delimiter. The "/" can't be used here as task doesn't have access to secrets
    // nested to the current DCOS_SPACE.
    // Secret path allowed characters: {secretPath:[A-Za-z0-9-/_]+}
    // More info: https://docs.mesosphere.com/1.9/security/#serv-job
    public static final String DELIMITER = "__";

    public SecretNameGenerator(
            String namespace,
            String taskInstanceName,
            String transportEncryptionName,
            String sanHash) {
        this.namespace = namespace;
        this.taskInstanceName = taskInstanceName;
        this.transportEncryptionName = transportEncryptionName;
        this.sanHash = sanHash;
    }

    public String getTaskSecretsNamespace() {
        return namespace;
    }

    public Collection<String> getAllSecretPaths() {
        return Arrays.asList(
                getCertificatePath(),
                getPrivateKeyPath(),
                getRootCACertPath(),
                getKeyStorePath(),
                getTrustStorePath()
        );
    }

    public String getCertificatePath() {
        return getSecretPath(SECRET_NAME_CERTIFICATE);
    }

    public String getPrivateKeyPath() {
        return getSecretPath(SECRET_NAME_PRIVATE_KEY);
    }

    public String getRootCACertPath() {
        return getSecretPath(SECRET_NAME_CA_CERT);
    }

    public String getKeyStorePath() {
        return getSecretPath(SECRET_NAME_KEYSTORE, true);
    }

    public String getTrustStorePath() {
        return getSecretPath(SECRET_NAME_TRUSTSTORE, true);
    }

    public String getCertificateMountPath() {
        return getMountPath("crt");
    }

    public String getPrivateKeyMountPath() {
        return getMountPath("key");
    }

    public String getRootCACertMountPath() {
        return getMountPath("ca");
    }

    public String getKeyStoreMountPath() {
        return getMountPath("keystore");
    }

    public String getTrustStoreMountPath() {
        return getMountPath("truststore");
    }

    private String getSecretPath(String name) {
        return getSecretPath(name, false);
    }

    private String getSecretPath(String name, boolean withBase64Prefix) {
        String[] names = Arrays.asList(sanHash, taskInstanceName, transportEncryptionName, name)
                .stream()
                .filter(item -> item != null)
                .filter(item -> !item.equals(""))
                .toArray(String[]::new);

        String fullName = String.join(DELIMITER, names);
        if (withBase64Prefix) {
            fullName = withBase64Prefix(fullName);
        }

        return String.format("%s/%s", getTaskSecretsNamespace(), fullName);
    }

    private String getMountPath(String suffix) {
        return String.format("%s.%s", transportEncryptionName, suffix);
    }

    // Prefix the secret name with __dcos_base64__ string so the secrets will get decoded by mesos secrets module.
    // See: DCOS-17621
    private String withBase64Prefix(String name) {
       return String.format("__dcos_base64__%s", name);
    }

    public static String getNamespaceFromEnvironment(String defaultNamespace, SchedulerFlags flags) {
        String secretNamespace = flags.getDcosSpaceLabelValue();

        if (secretNamespace.startsWith("/")) {
            secretNamespace = secretNamespace.substring(1);
        }

        if (secretNamespace.isEmpty()) {
            secretNamespace = defaultNamespace;
        }

        return secretNamespace;
    }

    /**
     * Creates SHA1 string representation of all {@link GeneralNames}.
     *
     * @param generalNames
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static String getSansHash(GeneralNames generalNames) throws NoSuchAlgorithmException {
        String allSans = Arrays.stream(generalNames.getNames())
                .map(name -> name.getName().toString())
                .collect(Collectors.joining(";"));
        MessageDigest hash = MessageDigest.getInstance("SHA-1");

        byte[] digest = hash.digest(allSans.getBytes(StandardCharsets.UTF_8));
        return new String(Hex.encode(digest), StandardCharsets.UTF_8);
    }
}
