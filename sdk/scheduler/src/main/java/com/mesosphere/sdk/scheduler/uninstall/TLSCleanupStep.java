package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.evaluate.security.TLSArtifact;
import com.mesosphere.sdk.offer.evaluate.security.TLSArtifactPaths;
import com.mesosphere.sdk.scheduler.plan.AbstractStep;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.Status;

import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A {@link TLSCleanupStep} removes all provisioned {@link TLSArtifact}s from secrets service in a given namespace.
 */
public class TLSCleanupStep extends AbstractStep {

  private final SecretsClient secretsClient;

  private final String secretsNamespace;

  /**
   * Creates a new instance with initial {@code status}.
   */
  TLSCleanupStep(SecretsClient secretsClient, String secretsNamespace, Optional<String> namespace) {
    super("tls-cleanup", namespace);
    this.secretsClient = secretsClient;
    this.secretsNamespace = secretsNamespace;
  }

  @Override
  public void start() {
    logger.info("Cleaning up TLS resources in namespace {}...", secretsNamespace);

    try {
      Collection<String> secretPathsToClean =
          TLSArtifactPaths.getKnownTLSArtifacts(secretsClient.list(secretsNamespace));
      if (secretPathsToClean.isEmpty()) {
        logger.info("No TLS resources to clean up.");
      } else {
        logger.info(
            "{} paths to clean in namespace {}:",
            secretPathsToClean.size(),
            secretsNamespace
        );
        for (String path : secretPathsToClean) {
          logger.info("Removing secret: '{}'", path);
          secretsClient.delete(secretsNamespace + "/" + path);
        }
      }

      setStatus(Status.COMPLETE);
    } catch (IOException e) {
      logger.error(
          String.format("Failed to clean up secrets in namespace %s", secretsNamespace),
          e
      );
      setStatus(Status.ERROR);
    }
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
    logger.debug(
        "Step {} ignoring irrelevant TaskStatus: {}",
        getName(),
        TextFormat.shortDebugString(status)
    );
  }
}
