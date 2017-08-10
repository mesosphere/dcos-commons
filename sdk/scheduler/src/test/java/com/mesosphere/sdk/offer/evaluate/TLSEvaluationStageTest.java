package com.mesosphere.sdk.offer.evaluate;


import com.mesosphere.sdk.dcos.secrets.SecretsException;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.evaluate.security.CertificateNamesGenerator;
import com.mesosphere.sdk.offer.evaluate.security.SecretNameGenerator;
import com.mesosphere.sdk.offer.evaluate.security.TLSArtifactsGenerator;
import com.mesosphere.sdk.offer.evaluate.security.TLSArtifactsPersister;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.OfferRequirementTestUtils;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.Mockito.*;

public class TLSEvaluationStageTest {

    @Mock
    private TLSArtifactsPersister tlsArtifactsPersisterMock;

    @Mock
    private TLSArtifactsGenerator tlsArtifactsGeneratorMock;

    private SecretNameGenerator secretNameGenerator;

    private CertificateNamesGenerator certificateNamesGenerator;

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);

        certificateNamesGenerator = new CertificateNamesGenerator(
                TestConstants.SERVICE_NAME,
                TestConstants.POD_TYPE + "-" + TestConstants.TASK_INDEX + "-" + TestConstants.TASK_NAME,
                Optional.empty(),
                Collections.emptyList());

        secretNameGenerator = new SecretNameGenerator(
                TestConstants.SERVICE_NAME,
                TestConstants.POD_TYPE + "-" + TestConstants.TASK_INDEX + "-" + TestConstants.TASK_NAME,
                "test-tls",
                SecretNameGenerator.getSansHash(certificateNamesGenerator.getSANs()));
    }

    private static PodInstanceRequirement getRequirementWithTransportEncryption(
            ResourceSet resourceSet, String type, int index,
            Collection<TransportEncryptionSpec> transportEncryptionSpecs) {
        TaskSpec taskSpec = DefaultTaskSpec.newBuilder()
                .name(TestConstants.TASK_NAME)
                .commandSpec(
                        DefaultCommandSpec.newBuilder(Collections.emptyMap())
                                .value(TestConstants.TASK_CMD)
                                .build())
                .goalState(GoalState.RUNNING)
                .resourceSet(resourceSet)
                .setTransportEncryption(transportEncryptionSpecs)
                .build();

        PodSpec podSpec = DefaultPodSpec.newBuilder("executor-uri")
                .type(type)
                .count(1)
                .tasks(Arrays.asList(taskSpec))
                .preReservedRole(Constants.ANY_ROLE)
                .build();

        PodInstance podInstance = new DefaultPodInstance(podSpec, index);

        List<String> taskNames = podInstance.getPod().getTasks().stream()
                .map(ts -> ts.getName())
                .collect(Collectors.toList());

        return PodInstanceRequirement.newBuilder(podInstance, taskNames).build();
    }

    private TLSEvaluationStage getDefaultTLSEvaluationStage() {
        return new TLSEvaluationStage(
                TestConstants.SERVICE_NAME,
                TestConstants.TASK_NAME,
                tlsArtifactsPersisterMock,
                tlsArtifactsGeneratorMock,
                OfferRequirementTestUtils.getTestSchedulerFlags());
    }

    private PodInfoBuilder getPodInfoBuilderForTransportEncryption(
            Collection<TransportEncryptionSpec> transportEncryptionSpecs) throws InvalidRequirementException {
        PodInstanceRequirement podInstanceRequirement = getRequirementWithTransportEncryption(
                PodInstanceRequirementTestUtils.getCpuResourceSet(1.0),
                TestConstants.POD_TYPE,
                0,
                transportEncryptionSpecs);

        PodInfoBuilder podInfoBuilder = new PodInfoBuilder(
                podInstanceRequirement,
                TestConstants.SERVICE_NAME,
                UUID.randomUUID(),
                OfferRequirementTestUtils.getTestSchedulerFlags(),
                Collections.emptyList(),
                TestConstants.FRAMEWORK_ID,
                true);

        return podInfoBuilder;
    }

    @Test
    public void testSuccessTLS() throws Exception {
        ArrayList<TransportEncryptionSpec> transportEncryptionSpecs = new ArrayList<>();
        transportEncryptionSpecs.add(new DefaultTransportEncryptionSpec
                .Builder()
                .name("test-tls")
                .type(TransportEncryptionSpec.Type.TLS)
                .build());

        TLSEvaluationStage evaluationStage = getDefaultTLSEvaluationStage();

        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedScalar(
                "cpus", 2.0);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);

        PodInfoBuilder podInfoBuilder = getPodInfoBuilderForTransportEncryption(
                transportEncryptionSpecs);

        EvaluationOutcome outcome = evaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        // Check that all TLS related methods were called
        verify(tlsArtifactsGeneratorMock, times(1))
                .generate(any());
        verify(tlsArtifactsPersisterMock, times(1))
                .cleanUpSecrets(Matchers.any());
        verify(tlsArtifactsPersisterMock, times(1))
                .isArtifactComplete(Matchers.any());
        verify(tlsArtifactsPersisterMock, times(1))
                .persist(Matchers.any(), Matchers.any());

        Protos.ContainerInfo executorContainer = podInfoBuilder
                .getTaskBuilder(TestConstants.TASK_NAME)
                .getExecutor()
                .getContainer();

        assertTLSArtifacts(executorContainer, secretNameGenerator);

        Protos.ContainerInfo taskContainer = podInfoBuilder
                .getTaskBuilder(TestConstants.TASK_NAME)
                .getContainer();

        assertTLSArtifacts(taskContainer, secretNameGenerator);
    }

    @Test
    public void testSuccessKeystore() throws Exception {
        ArrayList<TransportEncryptionSpec> transportEncryptionSpecs = new ArrayList<>();
        transportEncryptionSpecs.add(new DefaultTransportEncryptionSpec
                .Builder()
                .name("test-tls")
                .type(TransportEncryptionSpec.Type.KEYSTORE)
                .build());

        TLSEvaluationStage evaluationStage = getDefaultTLSEvaluationStage();

        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedScalar(
                "cpus", 2.0);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);

        PodInfoBuilder podInfoBuilder = getPodInfoBuilderForTransportEncryption(
                transportEncryptionSpecs);

        EvaluationOutcome outcome = evaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        // Check that all TLS related methods were called
        verify(tlsArtifactsGeneratorMock, times(1))
                .generate(any());
        verify(tlsArtifactsPersisterMock, times(1))
                .cleanUpSecrets(Matchers.any());
        verify(tlsArtifactsPersisterMock, times(1))
                .isArtifactComplete(Matchers.any());
        verify(tlsArtifactsPersisterMock, times(1))
                .persist(Matchers.any(), Matchers.any());

        Protos.ContainerInfo executorContainer = podInfoBuilder
                .getTaskBuilder(TestConstants.TASK_NAME)
                .getExecutor()
                .getContainer();

        assertKeystoreArtifacts(executorContainer, secretNameGenerator);

        Protos.ContainerInfo taskContainer = podInfoBuilder
                .getTaskBuilder(TestConstants.TASK_NAME)
                .getContainer();

        assertKeystoreArtifacts(taskContainer, secretNameGenerator);
    }

    @Test
    public void testArtifactsExists() throws Exception {
        when(tlsArtifactsPersisterMock
                        .isArtifactComplete(Matchers.any())).thenReturn(true);

        ArrayList<TransportEncryptionSpec> transportEncryptionSpecs = new ArrayList<>();
        transportEncryptionSpecs.add(new DefaultTransportEncryptionSpec
                .Builder()
                .name("test-tls")
                .type(TransportEncryptionSpec.Type.TLS)
                .build());

        TLSEvaluationStage evaluationStage = getDefaultTLSEvaluationStage();

        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedScalar(
                "cpus", 2.0);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);

        PodInfoBuilder podInfoBuilder = getPodInfoBuilderForTransportEncryption(
                transportEncryptionSpecs);

        EvaluationOutcome outcome = evaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        // Check that all TLS related methods were called
        verify(tlsArtifactsGeneratorMock, never())
                .generate(certificateNamesGenerator);
        verify(tlsArtifactsPersisterMock, never())
                .cleanUpSecrets(Matchers.any());
        verify(tlsArtifactsPersisterMock, times(1))
                .isArtifactComplete(Matchers.any());
        verify(tlsArtifactsPersisterMock, never())
                .persist(Matchers.any(), Matchers.any());

        Protos.ContainerInfo executorContainer = podInfoBuilder
                .getTaskBuilder(TestConstants.TASK_NAME)
                .getExecutor()
                .getContainer();

        assertTLSArtifacts(executorContainer, secretNameGenerator);

        Protos.ContainerInfo taskContainer = podInfoBuilder
                .getTaskBuilder(TestConstants.TASK_NAME)
                .getContainer();

        assertTLSArtifacts(taskContainer, secretNameGenerator);
    }

    @Test
    public void testFailure() throws Exception {
        doThrow(new SecretsException("test", "store", "path")).
            when(tlsArtifactsPersisterMock).cleanUpSecrets(Matchers.any());

        ArrayList<TransportEncryptionSpec> transportEncryptionSpecs = new ArrayList<>();
        transportEncryptionSpecs.add(new DefaultTransportEncryptionSpec
                .Builder()
                .name("test-tls")
                .type(TransportEncryptionSpec.Type.TLS)
                .build());

        TLSEvaluationStage evaluationStage = getDefaultTLSEvaluationStage();

        Protos.Resource offeredResource = ResourceTestUtils.getUnreservedScalar(
                "cpus", 2.0);
        Protos.Offer offer = OfferTestUtils.getOffer(offeredResource);

        PodInfoBuilder podInfoBuilder = getPodInfoBuilderForTransportEncryption(
                transportEncryptionSpecs);

        EvaluationOutcome outcome = evaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertFalse(outcome.isPassing());
    }

    private void assertTLSArtifacts(Protos.ContainerInfo container, SecretNameGenerator secretNameGenerator) {
        Protos.Volume volume = findVolumeWithContainerPath(container, secretNameGenerator.getCertificateMountPath())
                .get();
        Assert.assertEquals(
                volume.getSource().getSecret().getReference().getName(),
                secretNameGenerator.getCertificatePath());

        volume = findVolumeWithContainerPath(container, secretNameGenerator.getRootCACertMountPath())
                .get();
        Assert.assertEquals(
                volume.getSource().getSecret().getReference().getName(),
                secretNameGenerator.getRootCACertPath());

        volume = findVolumeWithContainerPath(container, secretNameGenerator.getPrivateKeyMountPath())
                .get();
        Assert.assertEquals(
                volume.getSource().getSecret().getReference().getName(),
                secretNameGenerator.getPrivateKeyPath());

        Assert.assertFalse(
                findVolumeWithContainerPath(
                        container, secretNameGenerator.getKeyStoreMountPath()).isPresent());
        Assert.assertFalse(
                findVolumeWithContainerPath(
                        container, secretNameGenerator.getTrustStoreMountPath()).isPresent());
    }

    private void assertKeystoreArtifacts(Protos.ContainerInfo container, SecretNameGenerator secretNameGenerator) {
        Protos.Volume volume = findVolumeWithContainerPath(container, secretNameGenerator.getKeyStoreMountPath())
                .get();
        Assert.assertEquals(
                volume.getSource().getSecret().getReference().getName(),
                secretNameGenerator.getKeyStorePath());

        volume = findVolumeWithContainerPath(container, secretNameGenerator.getTrustStoreMountPath())
                .get();
        Assert.assertEquals(
                volume.getSource().getSecret().getReference().getName(),
                secretNameGenerator.getTrustStorePath());

        Assert.assertFalse(
                findVolumeWithContainerPath(
                        container, secretNameGenerator.getCertificateMountPath()).isPresent());
        Assert.assertFalse(
                findVolumeWithContainerPath(
                        container, secretNameGenerator.getRootCACertMountPath()).isPresent());
        Assert.assertFalse(
                findVolumeWithContainerPath(
                        container, secretNameGenerator.getPrivateKeyMountPath()).isPresent());
    }

    private Optional<Protos.Volume> findVolumeWithContainerPath(
            Protos.ContainerInfo container, String path) {
        return container
                .getVolumesList()
                .stream()
                .filter(volume -> volume.getContainerPath().equals(path))
                .findAny();
    }

}
