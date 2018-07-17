package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.evaluate.security.TLSArtifact;
import com.mesosphere.sdk.offer.evaluate.security.TLSArtifactPaths;
import com.mesosphere.sdk.offer.evaluate.security.TLSArtifactsUpdater;
import com.mesosphere.sdk.scheduler.SchedulerConfig;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.PodTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.Mockito.*;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;


public class TLSEvaluationStageTest {

    @Mock private SchedulerConfig mockSchedulerConfig;
    @Mock private TLSArtifactsUpdater mockTLSArtifactsUpdater;

    private TLSArtifactPaths tlsArtifactPaths;
    private TLSEvaluationStage tlsEvaluationStage;

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mockSchedulerConfig.getAutoipTLD()).thenReturn("autoip.tld");

        // Expected sha1sum of the hostname:
        byte[] digest = MessageDigest.getInstance("SHA-1").digest(
                "pod-type-0-test-task-name.service-name.autoip.tld".getBytes(StandardCharsets.UTF_8));
        String sanHash = new String(Hex.encode(digest), StandardCharsets.UTF_8);

        tlsArtifactPaths = new TLSArtifactPaths(
                "test-namespace",
                TestConstants.POD_TYPE + "-" + TestConstants.TASK_INDEX + "-" + TestConstants.TASK_NAME,
                sanHash);
        tlsEvaluationStage = new TLSEvaluationStage(
                TestConstants.SERVICE_NAME,
                TestConstants.TASK_NAME,
                "test-namespace",
                mockTLSArtifactsUpdater,
                mockSchedulerConfig);
    }

    @Test
    public void testSuccessTLS() throws Exception {
        ArrayList<TransportEncryptionSpec> transportEncryptionSpecs = new ArrayList<>();
        transportEncryptionSpecs.add(DefaultTransportEncryptionSpec.newBuilder()
                .name("test-tls")
                .type(TransportEncryptionSpec.Type.TLS)
                .build());

        Protos.Offer offer = OfferTestUtils.getOffer(ResourceTestUtils.getUnreservedCpus(2.0));
        PodInfoBuilder podInfoBuilder = getPodInfoBuilderForTransportEncryption(transportEncryptionSpecs);

        EvaluationOutcome outcome = tlsEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        // Check that TLS update was invoked
        verify(mockTLSArtifactsUpdater).update(Matchers.any(), Matchers.any(), Matchers.eq("test-tls"));

        Protos.ContainerInfo executorContainer =
                podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getExecutor().getContainer();
        Assert.assertEquals(0, executorContainer.getVolumesCount());

        Protos.ContainerInfo taskContainer =
                podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getContainer();
        assertTLSArtifacts(taskContainer, tlsArtifactPaths, "test-tls");
    }

    @Test
    public void testSuccessKeystore() throws Exception {
        ArrayList<TransportEncryptionSpec> transportEncryptionSpecs = new ArrayList<>();
        transportEncryptionSpecs.add(DefaultTransportEncryptionSpec.newBuilder()
                .name("test-tls")
                .type(TransportEncryptionSpec.Type.KEYSTORE)
                .build());

        Protos.Offer offer = OfferTestUtils.getOffer(ResourceTestUtils.getUnreservedCpus(2.0));
        PodInfoBuilder podInfoBuilder = getPodInfoBuilderForTransportEncryption(transportEncryptionSpecs);

        EvaluationOutcome outcome = tlsEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        // Check that TLS update was invoked
        verify(mockTLSArtifactsUpdater).update(Matchers.any(), Matchers.any(), Matchers.eq("test-tls"));

        Protos.ContainerInfo executorContainer =
                podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getExecutor().getContainer();
        Assert.assertEquals(0, executorContainer.getVolumesCount());

        Protos.ContainerInfo taskContainer = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getContainer();
        assertKeystoreArtifacts(taskContainer, tlsArtifactPaths, "test-tls");
    }

    @Test
    public void testArtifactsExist() throws Exception {
        ArrayList<TransportEncryptionSpec> transportEncryptionSpecs = new ArrayList<>();
        transportEncryptionSpecs.add(DefaultTransportEncryptionSpec.newBuilder()
                .name("test-tls")
                .type(TransportEncryptionSpec.Type.TLS)
                .build());

        Protos.Offer offer = OfferTestUtils.getOffer(ResourceTestUtils.getUnreservedCpus(2.0));
        PodInfoBuilder podInfoBuilder = getPodInfoBuilderForTransportEncryption(transportEncryptionSpecs);

        EvaluationOutcome outcome = tlsEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        // Check that TLS update was invoked
        verify(mockTLSArtifactsUpdater).update(Matchers.any(), Matchers.any(), Matchers.eq("test-tls"));

        Protos.ContainerInfo executorContainer =
                podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getExecutor().getContainer();
        Assert.assertEquals(0, executorContainer.getVolumesCount());

        Protos.ContainerInfo taskContainer = podInfoBuilder.getTaskBuilder(TestConstants.TASK_NAME).getContainer();
        assertTLSArtifacts(taskContainer, tlsArtifactPaths, "test-tls");
    }

    @Test
    public void testFailure() throws Exception {
        doThrow(new IOException("test")).when(mockTLSArtifactsUpdater)
                .update(Matchers.any(), Matchers.any(), Matchers.any());

        ArrayList<TransportEncryptionSpec> transportEncryptionSpecs = new ArrayList<>();
        transportEncryptionSpecs.add(DefaultTransportEncryptionSpec.newBuilder()
                .name("test-tls")
                .type(TransportEncryptionSpec.Type.TLS)
                .build());

        Protos.Offer offer = OfferTestUtils.getOffer(ResourceTestUtils.getUnreservedCpus(2.0));
        PodInfoBuilder podInfoBuilder = getPodInfoBuilderForTransportEncryption(transportEncryptionSpecs);

        EvaluationOutcome outcome = tlsEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertFalse(outcome.isPassing());
    }

    @Test
    public void testMultipleTLSEvaluationStageDoesNotAddVolumes() throws Exception {
        ArrayList<TransportEncryptionSpec> transportEncryptionSpecs = new ArrayList<>();
        transportEncryptionSpecs.add(DefaultTransportEncryptionSpec.newBuilder()
                .name("test-tls")
                .type(TransportEncryptionSpec.Type.TLS)
                .build());
        Protos.Offer offer = OfferTestUtils.getOffer(ResourceTestUtils.getUnreservedCpus(2.0));

        PodInfoBuilder podInfoBuilder = getPodInfoBuilderForTransportEncryption(transportEncryptionSpecs);
        EvaluationOutcome outcome = tlsEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
                podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        int initialNumberOfVolumes = podInfoBuilder
                .getTaskBuilder(TestConstants.TASK_NAME)
                .getContainerBuilder()
                .getVolumesCount();

        outcome = tlsEvaluationStage.evaluate(
            new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)),
            podInfoBuilder);
        Assert.assertTrue(outcome.isPassing());

        int finalNumberOfVolumes = podInfoBuilder
                .getTaskBuilder(TestConstants.TASK_NAME)
                .getContainerBuilder()
                .getVolumesCount();

        assertThat(finalNumberOfVolumes, is(initialNumberOfVolumes));
    }

    private static PodInstanceRequirement getRequirementWithTransportEncryption(
            ResourceSet resourceSet,
            String type,
            int index,
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

        PodSpec podSpec = DefaultPodSpec.newBuilder(type, 1, Arrays.asList(taskSpec))
                .preReservedRole(Constants.ANY_ROLE)
                .build();

        PodInstance podInstance = new DefaultPodInstance(podSpec, index);
        List<String> taskNames = podInstance.getPod().getTasks().stream()
                .map(ts -> ts.getName())
                .collect(Collectors.toList());
        return PodInstanceRequirement.newBuilder(podInstance, taskNames).build();
    }

    private static PodInfoBuilder getPodInfoBuilderForTransportEncryption(
            final Collection<TransportEncryptionSpec> transportEncryptionSpecs) throws InvalidRequirementException {
        PodInstanceRequirement podInstanceRequirement = getRequirementWithTransportEncryption(
                PodInstanceRequirementTestUtils.getCpuResourceSet(1.0),
                TestConstants.POD_TYPE,
                0,
                transportEncryptionSpecs);

        return new PodInfoBuilder(
                podInstanceRequirement,
                TestConstants.SERVICE_NAME,
                UUID.randomUUID(),
                PodTestUtils.getTemplateUrlFactory(),
                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                Collections.emptyList(),
                TestConstants.FRAMEWORK_ID,
                Collections.emptyMap());
    }

    private static void assertTLSArtifacts(Protos.ContainerInfo container, TLSArtifactPaths secretPaths, String encryptionSpecName) {
        Assert.assertEquals(
                findSecretStorePath(container, TLSArtifact.CERTIFICATE.getMountPath(encryptionSpecName)).get(),
                secretPaths.getSecretStorePath(TLSArtifact.CERTIFICATE, encryptionSpecName));

        Assert.assertEquals(
                findSecretStorePath(container, TLSArtifact.CA_CERTIFICATE.getMountPath(encryptionSpecName)).get(),
                secretPaths.getSecretStorePath(TLSArtifact.CA_CERTIFICATE, encryptionSpecName));

        Assert.assertEquals(
                findSecretStorePath(container, TLSArtifact.PRIVATE_KEY.getMountPath(encryptionSpecName)).get(),
                secretPaths.getSecretStorePath(TLSArtifact.PRIVATE_KEY, encryptionSpecName));

        Assert.assertFalse(findSecretStorePath(container, TLSArtifact.KEYSTORE.getMountPath(encryptionSpecName)).isPresent());
        Assert.assertFalse(findSecretStorePath(container, TLSArtifact.TRUSTSTORE.getMountPath(encryptionSpecName)).isPresent());
    }

    private static void assertKeystoreArtifacts(Protos.ContainerInfo container, TLSArtifactPaths secretPaths, String encryptionSpecName) {
        Assert.assertEquals(
                findSecretStorePath(container, TLSArtifact.KEYSTORE.getMountPath(encryptionSpecName)).get(),
                secretPaths.getSecretStorePath(TLSArtifact.KEYSTORE, encryptionSpecName));

        Assert.assertEquals(
                findSecretStorePath(container, TLSArtifact.TRUSTSTORE.getMountPath(encryptionSpecName)).get(),
                secretPaths.getSecretStorePath(TLSArtifact.TRUSTSTORE, encryptionSpecName));

        Assert.assertFalse(findSecretStorePath(container, TLSArtifact.CERTIFICATE.getMountPath(encryptionSpecName)).isPresent());
        Assert.assertFalse(findSecretStorePath(container, TLSArtifact.CA_CERTIFICATE.getMountPath(encryptionSpecName)).isPresent());
        Assert.assertFalse(findSecretStorePath(container, TLSArtifact.PRIVATE_KEY.getMountPath(encryptionSpecName)).isPresent());
    }

    private static Optional<String> findSecretStorePath(Protos.ContainerInfo container, String path) {
        Optional<Protos.Volume> volume = container.getVolumesList().stream()
                .filter(v -> v.getContainerPath().equals(path))
                .findAny();
        return volume.isPresent()
                ? Optional.ofNullable(volume.get().getSource().getSecret().getReference().getName())
                : Optional.empty();
    }
}
