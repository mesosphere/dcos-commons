package com.mesosphere.sdk.offer.evaluate;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.dcos.DcosHttpClientBuilder;
import com.mesosphere.sdk.dcos.ca.DefaultCAClient;
import com.mesosphere.sdk.dcos.secrets.DefaultSecretsClient;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.evaluate.security.*;
import com.mesosphere.sdk.scheduler.SchedulerFlags;
import com.mesosphere.sdk.specification.NamedVIPSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import com.mesosphere.sdk.specification.TransportEncryptionSpec;
import com.mesosphere.sdk.specification.validation.ValidationUtils;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A {@link TLSEvaluationStage} is responsible for provisioning X.509 certificates, converting them to
 * PEM and KeyStore formats and injecting them to the container as a secret.
 */
public class TLSEvaluationStage implements OfferEvaluationStage {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String serviceName;
    private final String taskName;
    private final TLSArtifactsPersister tlsArtifactsPersister;
    private final TLSArtifactsGenerator tlsArtifactsGenerator;

    private final SchedulerFlags schedulerFlags;

    public static Builder newBuilder(String serviceName, SchedulerFlags flags) {
        return new Builder(serviceName, flags);
    }

    @VisibleForTesting
    TLSEvaluationStage(
            String serviceName,
            String taskName,
            TLSArtifactsPersister tlsArtifactsPersister,
            TLSArtifactsGenerator tlsArtifactsGenerator,
            SchedulerFlags schedulerFlags) {
        this.serviceName = serviceName;
        this.taskName = taskName;
        this.tlsArtifactsPersister = tlsArtifactsPersister;
        this.tlsArtifactsGenerator = tlsArtifactsGenerator;
        this.schedulerFlags = schedulerFlags;
    }

    @Override
    public EvaluationOutcome evaluate(MesosResourcePool mesosResourcePool, PodInfoBuilder podInfoBuilder) {
        TaskSpec taskSpec = findTaskSpec(podInfoBuilder);

        for (TransportEncryptionSpec transportEncryptionSpec : taskSpec.getTransportEncryption()) {
            String transportEncryptionName = transportEncryptionSpec.getName();

            SecretNameGenerator secretNameGenerator;

            try {
                CertificateNamesGenerator certificateNamesGenerator =
                        getCertificateNamesGenerator(podInfoBuilder, taskSpec);

                secretNameGenerator = new SecretNameGenerator(
                        SecretNameGenerator.getNamespaceFromEnvironment(schedulerFlags, serviceName),
                        TaskSpec.getInstanceName(podInfoBuilder.getPodInstance(), taskName),
                        transportEncryptionName,
                        certificateNamesGenerator.getSANs());

                if (!tlsArtifactsPersister.isArtifactComplete(secretNameGenerator)) {
                    tlsArtifactsPersister.cleanUpSecrets(secretNameGenerator);
                    TLSArtifacts tlsArtifacts = this.tlsArtifactsGenerator.generate(certificateNamesGenerator);
                    tlsArtifactsPersister.persist(secretNameGenerator, tlsArtifacts);
                } else {
                    logger.info("Task '{}' has already all secrets for '{}' TLS config",
                            taskName, transportEncryptionName);
                }
            } catch (Exception e) {
                logger.error(String.format("Failed to get certificate %s", taskName), e);
                return EvaluationOutcome.fail(
                        this, "Failed to store TLS artifacts for task %s because of exception: %s", taskName, e)
                        .build();
            }

            // Share keys to the task container
            podInfoBuilder
                    .getTaskBuilder(taskName)
                    .getContainerBuilder()
                    .addAllVolumes(getExecutorInfoSecretVolumes(transportEncryptionSpec, secretNameGenerator));

        }

        return EvaluationOutcome.pass(this, "TLS certificate created and added to the task").build();
    }

    private CertificateNamesGenerator getCertificateNamesGenerator(
            PodInfoBuilder podInfoBuilder, TaskSpec taskSpec) {
        List<NamedVIPSpec> vipPorts = getTaskVipsSpecs(taskSpec);

        Optional<String> discoveryName = Optional.empty();

        // Task can specify its own service discovery name
        if (taskSpec.getDiscovery().isPresent() && taskSpec.getDiscovery().get().getPrefix().isPresent()) {
            discoveryName = Optional.of(String.format("%s-%d",
                    taskSpec.getDiscovery().get().getPrefix().get(),
                    podInfoBuilder.getPodInstance().getIndex()));
        }

        return new CertificateNamesGenerator(
                serviceName,
                TaskSpec.getInstanceName(podInfoBuilder.getPodInstance(), taskSpec),
                discoveryName,
                vipPorts);
    }

    private List<NamedVIPSpec> getTaskVipsSpecs(TaskSpec taskSpec) {
        return taskSpec
                .getResourceSet()
                .getResources()
                .stream()
                .filter(resourceSpec -> resourceSpec instanceof NamedVIPSpec)
                .map(resourceSpec -> (NamedVIPSpec) resourceSpec)
                .collect(Collectors.toList());
    }

    private TaskSpec findTaskSpec(PodInfoBuilder podInfoBuilder) {
        return podInfoBuilder
                .getPodInstance()
                .getPod()
                .getTasks()
                .stream()
                .filter(task -> task.getName().equals(taskName))
                .findFirst()
                .get();
    }

    private Collection<Protos.Volume> getExecutorInfoSecretVolumes(
            TransportEncryptionSpec transportEncryptionSpec, SecretNameGenerator secretNameGenerator) {
        HashMap<String, String> tlsSecrets = new HashMap<>();

        if (transportEncryptionSpec.getType().equals(TransportEncryptionSpec.Type.TLS)) {
            tlsSecrets.put(secretNameGenerator.getCertificatePath(), secretNameGenerator.getCertificateMountPath());
            tlsSecrets.put(secretNameGenerator.getPrivateKeyPath(), secretNameGenerator.getPrivateKeyMountPath());
            tlsSecrets.put(secretNameGenerator.getRootCACertPath(), secretNameGenerator.getRootCACertMountPath());
        } else if (transportEncryptionSpec.getType().equals(TransportEncryptionSpec.Type.KEYSTORE)) {
            tlsSecrets.put(
                    secretNameGenerator.getKeyStorePath(), secretNameGenerator.getKeyStoreMountPath());
            tlsSecrets.put(
                    secretNameGenerator.getTrustStorePath(), secretNameGenerator.getTrustStoreMountPath());
        }

        Collection<Protos.Volume> volumes = new ArrayList<>();

        tlsSecrets.entrySet().forEach(tlsSecretEntry ->
                volumes.add(Protos.Volume.newBuilder()
                        .setSource(Protos.Volume.Source.newBuilder()
                                .setType(Protos.Volume.Source.Type.SECRET)
                                .setSecret(getReferenceSecret(tlsSecretEntry.getKey()))
                                .build())
                        .setContainerPath(tlsSecretEntry.getValue())
                        .setMode(Protos.Volume.Mode.RO)
                        .build())
        );

        return volumes;
    }

    private static Protos.Secret getReferenceSecret(String secretPath) {
        return Protos.Secret.newBuilder()
                .setType(Protos.Secret.Type.REFERENCE)
                .setReference(Protos.Secret.Reference.newBuilder().setName(secretPath))
                .build();
    }

    /**
     * A {@link Builder} allows to create a {@link TLSEvaluationStage} instance.
     */
    public static class Builder {

        private final String serviceName;
        private final SchedulerFlags schedulerFlags;

        @Valid
        @NotNull
        @Size(min = 1)
        private String taskName;

        private Builder(String serviceName, SchedulerFlags schedulerFlags) {
            this.serviceName = serviceName;
            this.schedulerFlags = schedulerFlags;
        }

        public Builder setTaskName(String taskName) {
            this.taskName = taskName;
            return this;
        }

        public TLSEvaluationStage build() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
            ValidationUtils.validate(this);

            Executor executor = Executor.newInstance(
                    new DcosHttpClientBuilder()
                            .setTokenProvider(schedulerFlags.getDcosAuthTokenProvider())
                            .setRedirectStrategy(new LaxRedirectStrategy() {
                                protected boolean isRedirectable(String method) {
                                    // Also treat PUT calls as redirectable
                                    return method.equalsIgnoreCase(HttpPut.METHOD_NAME) || super.isRedirectable(method);
                                }
                            })
                            .build());
            return new TLSEvaluationStage(
                    serviceName,
                    taskName,
                    new TLSArtifactsPersister(new DefaultSecretsClient(executor), serviceName),
                    new TLSArtifactsGenerator(KeyPairGenerator.getInstance("RSA"), new DefaultCAClient(executor)),
                    schedulerFlags);
        }
    }

}
