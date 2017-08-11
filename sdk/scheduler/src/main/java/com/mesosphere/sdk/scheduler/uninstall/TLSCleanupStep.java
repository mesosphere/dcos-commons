package com.mesosphere.sdk.scheduler.uninstall;

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
 * A {@link TLSCleanupStep} removes all provisioned {@link TLSArtifacts} from secrets service in a given
 * namespace.
 */
public class TLSCleanupStep extends AbstractStep {

    private final SecretsClient secretsClient;
    private final String namespace;
    private final Pattern pattern;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Creates a new instance with initial {@code status}.
     */
    TLSCleanupStep(Status status, SecretsClient secretsClient, String namespace) {
        super("tls-cleanup", status);
        this.secretsClient = secretsClient;
        this.namespace = namespace;
        this.pattern = createSecretNamePattern();
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        logger.info("Cleaning up TLS resources...");

        try {
            Collection<String> secretsToCleanCandidates = secretsClient.list(namespace);

            Collection<String> secretsPathsToClean = secretsToCleanCandidates
                    .stream()
                    .filter(secretPath -> pattern.matcher(secretPath).matches())
                    .collect(Collectors.toList());

            if (secretsPathsToClean.size() > 0) {
                logger.info(String.format("Paths to clean: "));
                for (String path : secretsPathsToClean) {
                    secretsClient.delete(namespace + "/" + path);
                    logger.info(String.format("Secret removed: '%s'", path));
                }
            } else {
                logger.info("No TLS resources to clean up...");
            }

            setStatus(Status.COMPLETE);
        } catch (SecretsException | IOException e) {
            logger.error(String.valueOf(e));
            setStatus(Status.ERROR);
        }

        return Optional.empty();
    }

    /**
     * Creates a regex pattern that matches possible {@link TLSArtifacts} paths in secrets store namespaced
     * to existing service.
     * @return Pattern
     */
    private Pattern createSecretNamePattern() {
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
    }
}
