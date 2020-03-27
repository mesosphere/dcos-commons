package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.dcos.DcosHttpClientBuilder;
import com.mesosphere.sdk.dcos.DcosHttpExecutor;
import com.mesosphere.sdk.dcos.clients.CertificateAuthorityClient;
import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.evaluate.security.CertificateNamesGenerator;
import com.mesosphere.sdk.offer.evaluate.security.TLSArtifactPaths;
import com.mesosphere.sdk.offer.evaluate.security.TLSArtifactsUpdater;
import com.mesosphere.sdk.offer.evaluate.security.TransportEncryptionArtifactPaths;
import com.mesosphere.sdk.offer.evaluate.security.TransportEncryptionEntry;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.TransportEncryptionSpec;

import com.google.common.annotations.VisibleForTesting;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A {@link TLSEvaluationStage} is responsible for provisioning X.509 certificates, converting them to
 * PEM and KeyStore formats and injecting them to the container as a secret.
 */
@SuppressWarnings({
    "checkstyle:InnerTypeLast",
    "checkstyle:IllegalCatch",
})
public class TLSEvaluationStage implements OfferEvaluationStage {

  private static final String GENERATED_ARTIFACTS_SUCCESS = "TLS certificates created and added to the task.";

  private static final String CUSTOM_ARTIFACTS_SUCCESS = "Custom transport artifacts added to the task.";

  private final Logger logger;

  private final String serviceName;

  private final String taskName;

  private final String namespace;

  private final TLSArtifactsUpdater tlsArtifactsUpdater;

  private final SchedulerConfig schedulerConfig;

  /**
   * Class for building {@link TLSEvaluationStage} instances for individual tasks that need it.
   */
  static class Builder {
    private final String serviceName;

    private final String namespace;

    private final TLSArtifactsUpdater tlsArtifactsUpdater;

    private final SchedulerConfig schedulerConfig;

    /**
     * Creates a new builder instance. Callers should avoid invoking this until/unless they have validated that TLS
     * functionality is needed.
     *
     * @throws IOException if the necessary clients could not be built, which may occur if the cluster doesn't
     *                     support TLS
     */
    public Builder(String serviceName, SchedulerConfig schedulerConfig) throws IOException {
      this.serviceName = serviceName;
      this.schedulerConfig = schedulerConfig;
      this.namespace = schedulerConfig.getSecretsNamespace(serviceName);
      DcosHttpExecutor executor = new DcosHttpExecutor(new DcosHttpClientBuilder()
          .setTokenProvider(schedulerConfig.getDcosAuthTokenProvider())
          .setRedirectStrategy(new LaxRedirectStrategy() {
            protected boolean isRedirectable(String method) {
              // Also treat PUT calls as redirectable
              return method.equalsIgnoreCase(HttpPut.METHOD_NAME) || super.isRedirectable(method);
            }
          }));
      this.tlsArtifactsUpdater = new TLSArtifactsUpdater(
          serviceName, new SecretsClient(executor), new CertificateAuthorityClient(executor));
    }

    public TLSEvaluationStage build(String taskName) {
      return new TLSEvaluationStage(
          serviceName,
          taskName,
          namespace,
          tlsArtifactsUpdater,
          schedulerConfig);
    }
  }

  @VisibleForTesting
  TLSEvaluationStage(String serviceName,
                     String taskName,
                     String namespace,
                     TLSArtifactsUpdater tlsArtifactsUpdater,
                     SchedulerConfig schedulerConfig)
  {
    this.logger = LoggingUtils.getLogger(getClass());
    this.serviceName = serviceName;
    this.taskName = taskName;
    this.namespace = namespace;
    this.tlsArtifactsUpdater = tlsArtifactsUpdater;
    this.schedulerConfig = schedulerConfig;
  }

  @Override
  public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool,
                                    PodInfoBuilder podInfoBuilder)
  {
    TaskSpec taskSpec = podInfoBuilder.getPodInstance().getPod().getTasks().stream()
        .filter(task -> task.getName().equals(taskName))
        .findFirst()
        .get();

    if (taskSpec.getTransportEncryption().isEmpty()) {
      return EvaluationOutcome.pass(
          this,
          "No Custom/TLS transport-handler specs found for task").build();
    }

    //We have transport specs, split these between ones we need to generate and ones
    //that are custom and pre-created.

    Collection<TransportEncryptionSpec> generateArtifacts = taskSpec.getTransportEncryption()
        .stream()
        .filter(spec -> spec.getType() != TransportEncryptionSpec.Type.CUSTOM)
        .collect(Collectors.toList());

    Collection<TransportEncryptionSpec> customArtifacts = taskSpec.getTransportEncryption()
        .stream()
        .filter(spec -> spec.getType() == TransportEncryptionSpec.Type.CUSTOM)
        .collect(Collectors.toList());

    EvaluationOutcome generatedArtifactsOutcome = generateTransportArtifacts(podInfoBuilder,
                                                                              taskSpec,
                                                                              generateArtifacts);
    EvaluationOutcome customArtifactsOutcome = generateCustomTransportArtifacts(podInfoBuilder,
                                                                                  taskSpec,
                                                                                  customArtifacts);

    //Return first failing outcome, otherwise return combined pass.
    if (!generatedArtifactsOutcome.isPassing()) {
      return generatedArtifactsOutcome;
    }

    if (!customArtifactsOutcome.isPassing()) {
      return customArtifactsOutcome;
    }

    return EvaluationOutcome.pass(
        this,
        String.format("TLS Evaluation Stage successful. %s %s",
            generatedArtifactsOutcome.isPassing() ? GENERATED_ARTIFACTS_SUCCESS : "",
            customArtifactsOutcome.isPassing() ? CUSTOM_ARTIFACTS_SUCCESS : "")
        ).build();
  }

  private EvaluationOutcome generateCustomTransportArtifacts(PodInfoBuilder podInfoBuilder,
                                                       TaskSpec taskSpec,
                                                       Collection<TransportEncryptionSpec> transportEncryptionSpecs)
  {
    logger.info("Processing Custom info for {} elements of {}",
        transportEncryptionSpecs.size(),
        transportEncryptionSpecs);

    List<TransportEncryptionEntry> paths = new ArrayList<>();

    for (TransportEncryptionSpec transportEncryptionSpec : transportEncryptionSpecs) {
      try {
        // Secret must be specified in this case, invalid to not specify it.
        String secret = transportEncryptionSpec.getSecret().get();

        // If a mount-path is specified, use it, other wise use the name which
        // cannot be empty.
        String mountPath = transportEncryptionSpec.getMountPath().isPresent() ?
            transportEncryptionSpec.getMountPath().get() :
            transportEncryptionSpec.getName();

        paths.add(new TransportEncryptionEntry(secret, mountPath));
      } catch (Exception e) {
        logger.error(String.format("Failed to process custom artifacts for %s", taskName), e);
        return EvaluationOutcome.fail(
            this,
            "Failed to process custom artifacts for task %s because of exception: %s",
            taskName,
            e).build();
      }
      Set<Protos.Volume> additionalVolumes = paths.stream()
          .map(TLSEvaluationStage::getSecretVolume)
          .collect(Collectors.toSet());

      addVolumesToPod(podInfoBuilder, additionalVolumes);
    }

    return EvaluationOutcome.pass(
        this,
        CUSTOM_ARTIFACTS_SUCCESS).build();
  }

  private EvaluationOutcome generateTransportArtifacts(PodInfoBuilder podInfoBuilder,
                                                       TaskSpec taskSpec,
                                                       Collection<TransportEncryptionSpec> transportEncryptionSpecs)
  {
    logger.info("Processing TLS info for {} elements of {}",
        transportEncryptionSpecs.size(),
        transportEncryptionSpecs);

    CertificateNamesGenerator certificateNamesGenerator =
        new CertificateNamesGenerator(
            serviceName, taskSpec,
            podInfoBuilder.getPodInstance(),
            schedulerConfig);

    //TODO@kjoshi: This is what needs to change between
    TLSArtifactPaths tlsArtifactPaths = new TLSArtifactPaths(
        namespace,
        CommonIdUtils.getTaskInstanceName(podInfoBuilder.getPodInstance(), taskName),
        certificateNamesGenerator.getSANsHash());

    //TODO@kjoshi here is where we add the new type of TransportEncryptionSpec
    for (TransportEncryptionSpec transportEncryptionSpec : transportEncryptionSpecs) {
      //transportEncryptionSpec.getType()
      try {
        tlsArtifactsUpdater.update(
            tlsArtifactPaths, certificateNamesGenerator, transportEncryptionSpec.getName());
      } catch (Exception e) {
        logger.error(String.format("Failed to process certificates for %s", taskName), e);
        return EvaluationOutcome.fail(
            this,
            "Failed to store TLS artifacts for task %s because of exception: %s",
            taskName,
            e).build();
      }

      Set<Protos.Volume> additionalVolumes = getExecutorInfoSecretVolumes(
          transportEncryptionSpec,
          tlsArtifactPaths
      );

      // Share keys to the task container.
      addVolumesToPod(podInfoBuilder, additionalVolumes);
    }

    return EvaluationOutcome.pass(
        this,
        GENERATED_ARTIFACTS_SUCCESS).build();
  }

  //TODO@kjoshi this is where the artifact paths are added to pod.
  private static Set<Protos.Volume> getExecutorInfoSecretVolumes(
      TransportEncryptionSpec spec, TransportEncryptionArtifactPaths tlsArtifactPaths)
  {
    Collection<TransportEncryptionEntry> paths =
        tlsArtifactPaths.getPathsForType(spec.getType(), spec.getName());
    return paths.stream()
        .map(TLSEvaluationStage::getSecretVolume)
        .collect(Collectors.toSet());
  }

  private static Protos.Volume getSecretVolume(TransportEncryptionEntry entry) {
    Protos.Volume.Builder volumeBuilder = Protos.Volume.newBuilder()
        .setContainerPath(entry.mountPath)
        .setMode(Protos.Volume.Mode.RO);
    Protos.Volume.Source.Builder sourceBuilder = volumeBuilder.getSourceBuilder()
        .setType(Protos.Volume.Source.Type.SECRET);
    sourceBuilder.getSecretBuilder()
        .setType(Protos.Secret.Type.REFERENCE)
        .getReferenceBuilder().setName(entry.secretStorePath);
    return volumeBuilder.build();
  }

  private void addVolumesToPod(PodInfoBuilder podInfoBuilder,
                               Set<Protos.Volume> additionalVolumes)
  {
    Set<Protos.Volume> existingVolumes = podInfoBuilder.getTaskBuilder(taskName)
        .getContainerBuilder()
        .getVolumesList()
        .stream()
        .collect(Collectors.toSet());

    logger.debug("Existing volumes for {}: {}",
        taskName,
        existingVolumes.stream().map(v -> v.getContainerPath()).toArray());

    logger.debug("Required volumes for {}: {}",
        taskName,
        additionalVolumes.stream().map(v -> v.getContainerPath()).toArray());

    if (additionalVolumes.removeAll(existingVolumes)) {
      logger.debug("Duplicate volumes for {} removed. Remaining: {}",
          taskName,
          additionalVolumes.stream().map(v -> v.getContainerPath()).toArray());
    }

    // Share artifact with the task container
    podInfoBuilder
        .getTaskBuilder(taskName)
        .getContainerBuilder()
        .addAllVolumes(additionalVolumes);

  }

  @VisibleForTesting
  protected String getTaskName() {
    return this.taskName;
  }
}
