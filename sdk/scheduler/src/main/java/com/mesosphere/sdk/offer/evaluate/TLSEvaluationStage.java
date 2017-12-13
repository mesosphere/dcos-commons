package com.mesosphere.sdk.offer.evaluate;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.dcos.DcosHttpClientBuilder;
import com.mesosphere.sdk.dcos.DcosHttpExecutor;
import com.mesosphere.sdk.dcos.clients.CertificateAuthorityClient;
import com.mesosphere.sdk.dcos.clients.SecretsClient;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.evaluate.security.*;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.TransportEncryptionSpec;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * A {@link TLSEvaluationStage} is responsible for provisioning X.509 certificates, converting them to
 * PEM and KeyStore formats and injecting them to the container as a secret.
 */
public class TLSEvaluationStage implements OfferEvaluationStage {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String serviceName;
    private final String taskName;
    private final String namespace;
    private final TLSArtifactsUpdater tlsArtifactsUpdater;

    /**
     * Class for building {@link TLSEvaluationStage} instances for individual tasks that need it.
     */
    static class Builder {
        private final String serviceName;
        private final String namespace;
        private final TLSArtifactsUpdater tlsArtifactsUpdater;

        /**
         * Creates a new builder instance. Callers should avoid invoking this until/unless they have validated that TLS
         * functionality is needed.
         *
         * @throws IOException if the necessary clients could not be built, which may occur if the cluster doesn't
         *                     support TLS
         */
        public Builder(String serviceName, SchedulerConfig schedulerConfig) throws IOException {
            this.serviceName = serviceName;
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
            return new TLSEvaluationStage(serviceName, taskName, namespace, tlsArtifactsUpdater);
        }
    }

    @VisibleForTesting
    TLSEvaluationStage(String serviceName, String taskName, String namespace, TLSArtifactsUpdater tlsArtifactsUpdater) {
        this.serviceName = serviceName;
        this.taskName = taskName;
        this.namespace = namespace;
        this.tlsArtifactsUpdater = tlsArtifactsUpdater;
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
                new CertificateNamesGenerator(serviceName, taskSpec, podInfoBuilder.getPodInstance());
        TLSArtifactPaths tlsArtifactPaths = new TLSArtifactPaths(
                namespace,
                TaskSpec.getInstanceName(podInfoBuilder.getPodInstance(), taskName),
                certificateNamesGenerator.getSANsHash());
        for (TransportEncryptionSpec transportEncryptionSpec : taskSpec.getTransportEncryption()) {
            try {
                tlsArtifactsUpdater.update(
                        tlsArtifactPaths, certificateNamesGenerator, transportEncryptionSpec.getName());
            } catch (Exception e) {
                logger.error(String.format("Failed to process certificates for %s", taskName), e);
                return EvaluationOutcome.fail(
                        this, "Failed to store TLS artifacts for task %s because of exception: %s", taskName, e)
                        .build();
            }

            // Share keys to the task container
            podInfoBuilder
                    .getTaskBuilder(taskName)
                    .getContainerBuilder()
                    .addAllVolumes(getExecutorInfoSecretVolumes(transportEncryptionSpec, tlsArtifactPaths));

        }

        return EvaluationOutcome.pass(this, "TLS certificate created and added to the task").build();
    }

    private static Collection<Protos.Volume> getExecutorInfoSecretVolumes(
            TransportEncryptionSpec spec, TLSArtifactPaths tlsArtifactPaths) {
        Collection<Protos.Volume> volumes = new ArrayList<>();
        for (TLSArtifactPaths.Entry entry : tlsArtifactPaths.getPathsForType(spec.getType(), spec.getName())) {
            volumes.add(getSecretVolume(entry));
        }
        return volumes;
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
