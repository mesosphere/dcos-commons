package com.mesosphere.sdk.offer.evaluate;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.dcos.DcosHttpClientBuilder;
import com.mesosphere.sdk.dcos.DcosHttpExecutor;
import com.mesosphere.sdk.dcos.clients.CertificateAuthorityClient;
import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.evaluate.security.*;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.TransportEncryptionSpec;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A {@link TLSEvaluationStage} is responsible for provisioning X.509 certificates, converting them to
 * PEM and KeyStore formats and injecting them to the container as a secret.
 */
public class TLSEvaluationStage implements OfferEvaluationStage {

    private static final Logger LOGGER = LoggingUtils.getLogger(TLSEvaluationStage.class);
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
            return new TLSEvaluationStage(serviceName, taskName, namespace, tlsArtifactsUpdater, schedulerConfig);
        }
    }

    @VisibleForTesting
    TLSEvaluationStage(String serviceName,
                       String taskName,
                       String namespace,
                       TLSArtifactsUpdater tlsArtifactsUpdater,
                       SchedulerConfig schedulerConfig) {
        this.logger = LoggingUtils.getLogger(getClass());
        this.serviceName = serviceName;
        this.taskName = taskName;
        this.namespace = namespace;
        this.tlsArtifactsUpdater = tlsArtifactsUpdater;
        this.schedulerConfig = schedulerConfig;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        TaskSpec taskSpec = podInfoBuilder.getPodInstance().getPod().getTasks().stream()
                .filter(task -> task.getName().equals(taskName))
                .findFirst()
                .get();
        if (taskSpec.getTransportEncryption().isEmpty()) {
            return EvaluationOutcome.pass(this, "No TLS specs found for task").build();
        }

        CertificateNamesGenerator certificateNamesGenerator =
                new CertificateNamesGenerator(serviceName, taskSpec, podInfoBuilder.getPodInstance(), schedulerConfig);
        TLSArtifactPaths tlsArtifactPaths = new TLSArtifactPaths(
                namespace,
                TaskSpec.getInstanceName(podInfoBuilder.getPodInstance(), taskName),
                certificateNamesGenerator.getSANsHash());

        Collection<TransportEncryptionSpec> transportEncryptionSpecs = taskSpec.getTransportEncryption();
        logger.info("Processing TLS info for {} elements of {}",
                    transportEncryptionSpecs.size(),
                    transportEncryptionSpecs);
        for (TransportEncryptionSpec transportEncryptionSpec : transportEncryptionSpecs) {
            try {
                tlsArtifactsUpdater.update(
                        tlsArtifactPaths, certificateNamesGenerator, transportEncryptionSpec.getName());
            } catch (Exception e) {
                logger.error(String.format("Failed to process certificates for %s", taskName), e);
                return EvaluationOutcome.fail(
                        this, "Failed to store TLS artifacts for task %s because of exception: %s", taskName, e)
                        .build();
            }

            Set<Protos.Volume> existingVolumes = podInfoBuilder.getTaskBuilder(taskName)
                    .getContainerBuilder()
                    .getVolumesList()
                    .stream()
                    .collect(Collectors.toSet());
            logger.info("Existing volumes for {}: {}", taskName, existingVolumes.stream().map(v -> v.getContainerPath()).toArray());

            Set<Protos.Volume> additionalVolumes = getExecutorInfoSecretVolumes(transportEncryptionSpec, tlsArtifactPaths);
            logger.info("Required volumes for {}: {}", taskName, additionalVolumes.stream().map(v -> v.getContainerPath()).toArray());

            if (additionalVolumes.removeAll(existingVolumes)) {
                logger.info("Duplicate volumes for {} removed. Remaining: {}", taskName, additionalVolumes.stream().map(v -> v.getContainerPath()).toArray());
            }

            // Share keys to the task container
            podInfoBuilder
                    .getTaskBuilder(taskName)
                    .getContainerBuilder()
                    .addAllVolumes(additionalVolumes);
        }

        return EvaluationOutcome.pass(this, "TLS certificate created and added to the task").build();
    }

    private static Set<Protos.Volume> getExecutorInfoSecretVolumes(
            TransportEncryptionSpec spec, TLSArtifactPaths tlsArtifactPaths) {

        Collection<TLSArtifactPaths.Entry> paths = tlsArtifactPaths.getPathsForType(spec.getType(), spec.getName());
        return paths.stream()
                .map(TLSEvaluationStage::getSecretVolume)
                .collect(Collectors.toSet());
    }

    private static Protos.Volume getSecretVolume(TLSArtifactPaths.Entry entry) {
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
}
