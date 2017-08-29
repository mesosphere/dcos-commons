package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.specification.*;
import com.mesosphere.sdk.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.when;

public class TaskVolumesCannotChangeTest {
    private static final int DISK_SIZE_MB = 1000;

    private static final ConfigValidator<ServiceSpec> VALIDATOR = new TaskVolumesCannotChange();

    // slight differences between volumes:
    private static final VolumeSpec VOLUME1 = new DefaultVolumeSpec(
            DISK_SIZE_MB,
            VolumeSpec.Type.MOUNT,
            "some_path",
            "role",
            "*",
            "principal");
    private static final VolumeSpec VOLUME2 = new DefaultVolumeSpec(
            DISK_SIZE_MB + 3,
            VolumeSpec.Type.MOUNT,
            "some_path",
            "role",
            "*",
            "principal");
    private static final VolumeSpec VOLUME3 = new DefaultVolumeSpec(
            DISK_SIZE_MB,
            VolumeSpec.Type.ROOT,
            "some_path",
            "role",
            "*",
            "principal");

    @Mock private PodSpec mockPodSpec1;
    @Mock private PodSpec mockPodSpec2;
    @Mock private PodSpec mockPodSpec3;
    @Mock private TaskSpec mockTaskSpec1;
    @Mock private TaskSpec mockTaskSpec2;
    @Mock private TaskSpec mockTaskSpec3;
    @Mock private ResourceSet mockResourceSet1;
    @Mock private ResourceSet mockResourceSet2;
    @Mock private ResourceSet mockResourceSet3;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);

        // Task volume validation is based on podType-taskName names
        // so to simulate the same config with changing task volumes
        // we keep the pod type uniform across the pods.
        when(mockPodSpec1.getType()).thenReturn(TestConstants.POD_TYPE);
        when(mockPodSpec2.getType()).thenReturn(TestConstants.POD_TYPE);
        when(mockPodSpec3.getType()).thenReturn(TestConstants.POD_TYPE);

        when(mockTaskSpec1.getName()).thenReturn(TestConstants.TASK_NAME + "-1");

        when(mockResourceSet1.getVolumes()).thenReturn(Arrays.asList(VOLUME1));
        when(mockResourceSet2.getVolumes()).thenReturn(Arrays.asList(VOLUME2, VOLUME3));
        when(mockResourceSet3.getVolumes()).thenReturn(Collections.emptyList());

        when(mockTaskSpec1.getResourceSet()).thenReturn(mockResourceSet1);
        when(mockTaskSpec2.getResourceSet()).thenReturn(mockResourceSet2);
        when(mockTaskSpec3.getResourceSet()).thenReturn(mockResourceSet3);

        when(mockPodSpec1.getTasks()).thenReturn(Arrays.asList(mockTaskSpec1));
        when(mockPodSpec2.getTasks()).thenReturn(Arrays.asList(mockTaskSpec2));
        when(mockPodSpec3.getTasks()).thenReturn(Arrays.asList(mockTaskSpec3));
    }

    @Test
    public void testUnchangedVolumes() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .build();

        ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec2))
                .build();


        Assert.assertTrue(VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec1).isEmpty());
        Assert.assertTrue(VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec2).isEmpty());

        // the tasks are reshuffled, but each task's volumes don't change!:
        Assert.assertTrue(VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).isEmpty());
        Assert.assertTrue(VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec1).isEmpty());
    }

    @Test
    public void testChangedVolumes() throws InvalidRequirementException {

        // "Change" only detected when mockPodSpec1 AND mockPodSpec2 are compared.

        ServiceSpec serviceSpec1 = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .build();
        ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec2))
                .build();
        ServiceSpec serviceSpec3 = DefaultServiceSpec.newBuilder()
                .name("svc3")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec3))
                .build();

        when(mockTaskSpec2.getName()).thenReturn(TestConstants.TASK_NAME + "-1");
        when(mockTaskSpec3.getName()).thenReturn(TestConstants.TASK_NAME + "-1");

        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).size());
        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec3).size());
        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec1).size());
        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(serviceSpec2), serviceSpec3).size());
        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(serviceSpec3), serviceSpec1).size());
        Assert.assertEquals(1, VALIDATOR.validate(Optional.of(serviceSpec3), serviceSpec2).size());
    }

    @Test
    public void testDuplicateTasksInService() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .build();
        ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec2))
                .build();

        when(mockTaskSpec2.getName()).thenReturn(TestConstants.TASK_NAME + "-1");
        when(mockPodSpec2.getTasks()).thenReturn(Arrays.asList(mockTaskSpec1, mockTaskSpec2));

        Assert.assertEquals(2, VALIDATOR.validate(Optional.of(serviceSpec1), serviceSpec2).size());
    }
}
