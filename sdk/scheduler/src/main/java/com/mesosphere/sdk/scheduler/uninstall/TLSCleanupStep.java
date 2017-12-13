package com.mesosphere.sdk.scheduler.uninstall;

import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.evaluate.security.TLSArtifact;
import com.mesosphere.sdk.offer.evaluate.security.TLSArtifactPaths;
import com.mesosphere.sdk.scheduler.plan.AbstractStep;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * A {@link TLSCleanupStep} removes all provisioned {@link TLSArtifact}s from secrets service in a given namespace.
 */
public class TLSCleanupStep extends AbstractStep {

    private final SecretsClient secretsClient;
    private final String namespace;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Creates a new instance with initial {@code status}.
     */
    TLSCleanupStep(SecretsClient secretsClient, String namespace) {
        super("tls-cleanup", Status.PENDING);
        this.secretsClient = secretsClient;
        this.namespace = namespace;
    }

    @Override
    public Optional<PodInstanceRequirement> start() {
        logger.info("Cleaning up TLS resources in namespace {}...", namespace);

        try {
            Collection<String> secretPathsToClean =
                    TLSArtifactPaths.getKnownTLSArtifacts(secretsClient.list(namespace));
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
        } catch (IOException e) {
            logger.error(String.format("Failed to clean up secrets in namespace %s", namespace), e);
            setStatus(Status.ERROR);
        }

        return Optional.empty();
    }

    @Override
    public Optional<PodInstanceRequirement> getPodInstanceRequirement() {
        return Optional.empty();
    }

    @Override
    public void updateOfferStatus(Collection<OfferRecommendation> recommendations) {
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
