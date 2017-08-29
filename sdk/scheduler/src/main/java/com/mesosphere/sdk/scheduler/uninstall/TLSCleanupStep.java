package com.mesosphere.sdk.scheduler.uninstall;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.dcos.SecretsClient;
import com.mesosphere.sdk.dcos.secrets.SecretsException;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.evaluate.security.SecretNameGenerator;
import com.mesosphere.sdk.offer.evaluate.security.TLSArtifacts;
import com.mesosphere.sdk.scheduler.plan.AbstractStep;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A {@link TLSCleanupStep} removes all provisioned {@link TLSArtifacts} from secrets service in a given namespace.
 */
public class TLSCleanupStep extends AbstractStep {

    private static final Pattern PATTERN = createSecretNamePattern();

    private final SecretsClient secretsClient;
    private final String namespace;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Creates a new instance with initial {@code status}.
     */
    TLSCleanupStep(Status status, SecretsClient secretsClient, String namespace) {
        super("tls-cleanup", status);
        this.secretsClient = secretsClient;
        this.namespace = namespace;
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        logger.info("Cleaning up TLS resources in namespace {}...", namespace);

        try {
            Collection<String> secretPathsToClean = secretsClient.list(namespace).stream()
                    .filter(secretPath -> PATTERN.matcher(secretPath).matches())
                    .collect(Collectors.toList());

            if (secretPathsToClean.isEmpty()) {
                logger.info("No TLS resources to clean up.");
            } else {
                logger.info("{} paths to clean in namespace {}:", secretPathsToClean.size(), namespace);
                for (String path : secretPathsToClean) {
                    logger.info("Removing secret: '{}'", path);
                    secretsClient.delete(namespace + "/" + path);
                }
            }

            setStatus(Status.COMPLETE);
        } catch (SecretsException | IOException e) {
            logger.error(String.format("Failed to clean up secrets in namespace %s", namespace), e);
            setStatus(Status.ERROR);
        }

        return Optional.empty();
    }

    /**
     * Creates a regex pattern that matches possible {@link TLSArtifacts} paths in secrets store namespaced
     * to existing service.
     */
    private static Pattern createSecretNamePattern() {
        List<String> possibleSecretNames = Arrays.asList(
                SecretNameGenerator.SECRET_NAME_CERTIFICATE,
                SecretNameGenerator.SECRET_NAME_PRIVATE_KEY,
                SecretNameGenerator.SECRET_NAME_CA_CERT,
                SecretNameGenerator.SECRET_NAME_KEYSTORE,
                SecretNameGenerator.SECRET_NAME_TRUSTSTORE
        );

        String regex = String.format("^.+%s(?:%s)$",
                SecretNameGenerator.DELIMITER,
                String.join("|", possibleSecretNames));

        return Pattern.compile(regex);
    }

    @Override
    public Optional<PodInstanceRequirement> getPodInstanceRequirement() {
        return Optional.empty();
    }

    @Override
    public void updateOfferStatus(Collection<OfferRecommendation> recommendations) {
    }

    @Override
    public Optional<PodInstanceRequirement> getAsset() {
        return getPodInstanceRequirement();
    }

    @Override
    public List<String> getErrors() {
        return Collections.emptyList();
    }

    @Override
    public void update(Protos.TaskStatus status) {
        logger.debug("Step {} ignoring irrelevant TaskStatus: {}", getName(), TextFormat.shortDebugString(status));
    }
}
