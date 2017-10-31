package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.evaluate.security.TLSArtifact;
import com.mesosphere.sdk.offer.evaluate.security.TLSArtifactPaths;
import com.mesosphere.sdk.offer.evaluate.security.TLSArtifactsUpdater;
import com.mesosphere.sdk.scheduler.plan.DefaultPodInstance;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirement;
import com.mesosphere.sdk.scheduler.plan.PodInstanceRequirementTestUtils;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.OfferTestUtils;
import com.mesosphere.sdk.testutils.ResourceTestUtils;
import com.mesosphere.sdk.testutils.SchedulerConfigTestUtils;
import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.Mockito.*;

public class TLSEvaluationStageTest {

    @Mock private TLSArtifactsUpdater mockTLSArtifactsUpdater;

    private TLSArtifactPaths tlsArtifactPaths;
    private TLSEvaluationStage tlsEvaluationStage;

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);

        // echo -n "pod-type-0-test-task-name.service-name.autoip.dcos.thisdcos.directory" | sha1sum
        String sanHash = "8ffc618c478beb31a043d978652d7bc571fedfe2";
        tlsArtifactPaths = new TLSArtifactPaths(
                "test-namespace",
                TestConstants.POD_TYPE + "-" + TestConstants.TASK_INDEX + "-" + TestConstants.TASK_NAME,
                sanHash);
        tlsEvaluationStage = new TLSEvaluationStage(
                TestConstants.SERVICE_NAME, TestConstants.TASK_NAME, "test-namespace", mockTLSArtifactsUpdater);
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

    private PodInfoBuilder getPodInfoBuilderForTransportEncryption(
            Collection<TransportEncryptionSpec> transportEncryptionSpecs) throws InvalidRequirementException {
        PodInstanceRequirement podInstanceRequirement = getRequirementWithTransportEncryption(
                PodInstanceRequirementTestUtils.getCpuResourceSet(1.0),
                TestConstants.POD_TYPE,
                0,
                transportEncryptionSpecs);

        return new PodInfoBuilder(
                podInstanceRequirement,
                TestConstants.SERVICE_NAME,
                UUID.randomUUID(),
                SchedulerConfigTestUtils.getTestSchedulerConfig(),
                Collections.emptyList(),
                TestConstants.FRAMEWORK_ID,
                true,
                Collections.emptyMap());
    }

    @Test
    public void testSuccessTLS() throws Exception {
        ArrayList<TransportEncryptionSpec> transportEncryptionSpecs = new ArrayList<>();
        transportEncryptionSpecs.add(new DefaultTransportEncryptionSpec
                .Builder()
                .name("test-tls")
                .type(TransportEncryptionSpec.Type.TLS)
                .build());

        Protos.Offer offer = OfferTestUtils.getOffer(ResourceTestUtils.getUnreservedCpus(2.0));
        PodInfoBuilder podInfoBuilder = getPodInfoBuilderForTransportEncryption(transportEncryptionSpecs);

        EvaluationOutcome outcome = tlsEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
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
        transportEncryptionSpecs.add(new DefaultTransportEncryptionSpec.Builder()
                .name("test-tls")
                .type(TransportEncryptionSpec.Type.KEYSTORE)
                .build());

        Protos.Offer offer = OfferTestUtils.getOffer(ResourceTestUtils.getUnreservedCpus(2.0));
        PodInfoBuilder podInfoBuilder = getPodInfoBuilderForTransportEncryption(transportEncryptionSpecs);

        EvaluationOutcome outcome = tlsEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
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
        transportEncryptionSpecs.add(new DefaultTransportEncryptionSpec
                .Builder()
                .name("test-tls")
                .type(TransportEncryptionSpec.Type.TLS)
                .build());

        Protos.Offer offer = OfferTestUtils.getOffer(ResourceTestUtils.getUnreservedCpus(2.0));
        PodInfoBuilder podInfoBuilder = getPodInfoBuilderForTransportEncryption(transportEncryptionSpecs);

        EvaluationOutcome outcome = tlsEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
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
        transportEncryptionSpecs.add(new DefaultTransportEncryptionSpec
                .Builder()
                .name("test-tls")
                .type(TransportEncryptionSpec.Type.TLS)
                .build());

        Protos.Offer offer = OfferTestUtils.getOffer(ResourceTestUtils.getUnreservedCpus(2.0));
        PodInfoBuilder podInfoBuilder = getPodInfoBuilderForTransportEncryption(transportEncryptionSpecs);

        EvaluationOutcome outcome = tlsEvaluationStage.evaluate(
                new MesosResourcePool(offer, Optional.of(Constants.ANY_ROLE)), podInfoBuilder);
        Assert.assertFalse(outcome.isPassing());
    }

    private void assertTLSArtifacts(Protos.ContainerInfo container, TLSArtifactPaths secretPaths, String encryptionSpecName) {
        Protos.Volume volume = findVolumeWithContainerPath(container, TLSArtifact.CERTIFICATE.getMountPath(encryptionSpecName)).get();
        Assert.assertEquals(
                volume.getSource().getSecret().getReference().getName(),
                secretPaths.getSecretStorePath(TLSArtifact.CERTIFICATE, encryptionSpecName));

        volume = findVolumeWithContainerPath(container, TLSArtifact.CA_CERTIFICATE.getMountPath(encryptionSpecName)).get();
        Assert.assertEquals(
                volume.getSource().getSecret().getReference().getName(),
                secretPaths.getSecretStorePath(TLSArtifact.CA_CERTIFICATE, encryptionSpecName));

        volume = findVolumeWithContainerPath(container, TLSArtifact.PRIVATE_KEY.getMountPath(encryptionSpecName)).get();
        Assert.assertEquals(
                volume.getSource().getSecret().getReference().getName(),
                secretPaths.getSecretStorePath(TLSArtifact.PRIVATE_KEY, encryptionSpecName));

        Assert.assertFalse(findVolumeWithContainerPath(container, TLSArtifact.KEYSTORE.getMountPath(encryptionSpecName)).isPresent());
        Assert.assertFalse(findVolumeWithContainerPath(container, TLSArtifact.TRUSTSTORE.getMountPath(encryptionSpecName)).isPresent());
    }

    private void assertKeystoreArtifacts(Protos.ContainerInfo container, TLSArtifactPaths secretPaths, String encryptionSpecName) {
        Protos.Volume volume = findVolumeWithContainerPath(container, TLSArtifact.KEYSTORE.getMountPath(encryptionSpecName)).get();
        Assert.assertEquals(
                volume.getSource().getSecret().getReference().getName(),
                secretPaths.getSecretStorePath(TLSArtifact.KEYSTORE, encryptionSpecName));

        volume = findVolumeWithContainerPath(container, TLSArtifact.TRUSTSTORE.getMountPath(encryptionSpecName)).get();
        Assert.assertEquals(
                volume.getSource().getSecret().getReference().getName(),
                secretPaths.getSecretStorePath(TLSArtifact.TRUSTSTORE, encryptionSpecName));

        Assert.assertFalse(findVolumeWithContainerPath(container, TLSArtifact.CERTIFICATE.getMountPath(encryptionSpecName)).isPresent());
        Assert.assertFalse(findVolumeWithContainerPath(container, TLSArtifact.CA_CERTIFICATE.getMountPath(encryptionSpecName)).isPresent());
        Assert.assertFalse(findVolumeWithContainerPath(container, TLSArtifact.PRIVATE_KEY.getMountPath(encryptionSpecName)).isPresent());
    }

    private Optional<Protos.Volume> findVolumeWithContainerPath(Protos.ContainerInfo container, String path) {
        return container.getVolumesList().stream()
                .filter(volume -> volume.getContainerPath().equals(path))
                .findAny();
    }
}
