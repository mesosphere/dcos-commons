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

import static org.mockito.Mockito.when;

public class VolumeCannotBeEmptyTest {

    private static final int DISK_SIZE_MB = 1000;

    private static final ConfigValidator<ServiceSpec> VALIDATOR = new VolumePathCannotBeEmpty();

    // slight differences between volumes:
    private static final VolumeSpec VOLUME0 = new DefaultVolumeSpec(
            DISK_SIZE_MB, VolumeSpec.Type.MOUNT, "/path/to/volume0", "role", "principal", "VOLUME");
    private static final VolumeSpec VOLUME1 = new DefaultVolumeSpec(
            DISK_SIZE_MB, VolumeSpec.Type.MOUNT, "/path/to/volume1", "role", "principal", "VOLUME");
    private static final VolumeSpec VOLUME2 = new DefaultVolumeSpec(
            DISK_SIZE_MB, VolumeSpec.Type.MOUNT, "", "role", "principal", "VOLUME");
    private static final VolumeSpec VOLUME3 = new DefaultVolumeSpec(
            DISK_SIZE_MB, VolumeSpec.Type.ROOT, " ", "role", "principal", "VOLUME");


    @Mock private ResourceSet mockResourceSetSpec0;
    @Mock private ResourceSet mockResourceSetSpec1;
    @Mock private ResourceSet mockResourceSetSpec2;
    @Mock private ResourceSet mockResourceSetSpec3;


    @Mock private ResourceSet mockResourceSetSpec01;
    @Mock private ResourceSet mockResourceSetSpec12;
    @Mock private ResourceSet mockResourceSetSpec13;
    @Mock private ResourceSet mockResourceSetSpec23;

    @Mock private TaskSpec mockTaskSpec0;
    @Mock private TaskSpec mockTaskSpec1;
    @Mock private TaskSpec mockTaskSpec2;
    @Mock private TaskSpec mockTaskSpec3;

    @Mock private TaskSpec mockTaskSpec01;
    @Mock private TaskSpec mockTaskSpec12;
    @Mock private TaskSpec mockTaskSpec13;
    @Mock private TaskSpec mockTaskSpec23;

    @Mock private PodSpec mockPodSpec0;
    @Mock private PodSpec mockPodSpec1;
    @Mock private PodSpec mockPodSpec2;
    @Mock private PodSpec mockPodSpec3;
    @Mock private PodSpec mockPodSpec01;
    @Mock private PodSpec mockPodSpec12;
    @Mock private PodSpec mockPodSpec13;
    @Mock private PodSpec mockPodSpec23;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);

        when(mockResourceSetSpec0.getVolumes()).thenReturn(Arrays.asList(VOLUME0));
        when(mockResourceSetSpec1.getVolumes()).thenReturn(Arrays.asList(VOLUME1));
        when(mockResourceSetSpec2.getVolumes()).thenReturn(Arrays.asList(VOLUME2));
        when(mockResourceSetSpec3.getVolumes()).thenReturn(Arrays.asList(VOLUME3));



        when(mockResourceSetSpec01.getVolumes()).thenReturn(Arrays.asList(VOLUME0, VOLUME1));
        when(mockResourceSetSpec12.getVolumes()).thenReturn(Arrays.asList(VOLUME1, VOLUME2));
        when(mockResourceSetSpec13.getVolumes()).thenReturn(Arrays.asList(VOLUME1, VOLUME2));
        when(mockResourceSetSpec23.getVolumes()).thenReturn(Arrays.asList(VOLUME2, VOLUME3));

        when(mockTaskSpec0.getResourceSet()).thenReturn(mockResourceSetSpec0);
        when(mockTaskSpec1.getResourceSet()).thenReturn(mockResourceSetSpec1);
        when(mockTaskSpec2.getResourceSet()).thenReturn(mockResourceSetSpec2);
        when(mockTaskSpec3.getResourceSet()).thenReturn(mockResourceSetSpec3);

        when(mockTaskSpec01.getResourceSet()).thenReturn(mockResourceSetSpec01);
        when(mockTaskSpec12.getResourceSet()).thenReturn(mockResourceSetSpec12);
        when(mockTaskSpec13.getResourceSet()).thenReturn(mockResourceSetSpec13);
        when(mockTaskSpec23.getResourceSet()).thenReturn(mockResourceSetSpec23);

        when(mockPodSpec0.getTasks()).thenReturn(Arrays.asList(mockTaskSpec0));
        when(mockPodSpec1.getTasks()).thenReturn(Arrays.asList(mockTaskSpec1));
        when(mockPodSpec2.getTasks()).thenReturn(Arrays.asList(mockTaskSpec2));
        when(mockPodSpec3.getTasks()).thenReturn(Arrays.asList(mockTaskSpec3));
        when(mockPodSpec01.getTasks()).thenReturn(Arrays.asList(mockTaskSpec01));
        when(mockPodSpec12.getTasks()).thenReturn(Arrays.asList(mockTaskSpec12));
        when(mockPodSpec13.getTasks()).thenReturn(Arrays.asList(mockTaskSpec13));
        when(mockPodSpec23.getTasks()).thenReturn(Arrays.asList(mockTaskSpec23));


        when(mockPodSpec0.getType()).thenReturn("pod0");
        when(mockPodSpec1.getType()).thenReturn("pod1");
        when(mockPodSpec2.getType()).thenReturn("pod2");
        when(mockPodSpec3.getType()).thenReturn("pod3");
        when(mockPodSpec01.getType()).thenReturn("pod01");
        when(mockPodSpec12.getType()).thenReturn("pod12");
        when(mockPodSpec13.getType()).thenReturn("pod13");
        when(mockPodSpec23.getType()).thenReturn("pod23");
    }

    @Test
    public void testVolumePathCannotBeEmpty() throws InvalidRequirementException {
        ServiceSpec serviceSpec0 = DefaultServiceSpec.newBuilder()
                .name("svc0")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec0))
                .apiPort(1234)
                .build();

        ServiceSpec serviceSpec1 = DefaultServiceSpec.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .apiPort(1234)
                .build();

        ServiceSpec serviceSpec2 = DefaultServiceSpec.newBuilder()
                .name("svc2")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec2))
                .apiPort(1234)
                .build();

        ServiceSpec serviceSpec3 = DefaultServiceSpec.newBuilder()
                .name("svc3")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec3))
                .apiPort(1234)
                .build();

        ServiceSpec serviceSpecR01 = DefaultServiceSpec.newBuilder()
                .name("svc01")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec01))
                .apiPort(1234)
                .build();

        ServiceSpec serviceSpecR12 = DefaultServiceSpec.newBuilder()
                .name("svc12")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec12))
                .apiPort(1234)
                .build();

        ServiceSpec serviceSpecR13 = DefaultServiceSpec.newBuilder()
                .name("svc13")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec13))
                .apiPort(1234)
                .build();

        ServiceSpec serviceSpecR23 = DefaultServiceSpec.newBuilder()
                .name("svc23")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec23))
                .apiPort(1234)
                .build();

        ServiceSpec serviceSpecM01 = DefaultServiceSpec.newBuilder()
                .name("svc01")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec0, mockPodSpec1))
                .apiPort(1234)
                .build();

        ServiceSpec serviceSpecM12 = DefaultServiceSpec.newBuilder()
                .name("svc12")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .apiPort(1234)
                .build();

        ServiceSpec serviceSpecM13 = DefaultServiceSpec.newBuilder()
                .name("svc13")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec3))
                .apiPort(1234)
                .build();

        ServiceSpec serviceSpecM23 = DefaultServiceSpec.newBuilder()
                .name("svc23")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec2, mockPodSpec3))
                .apiPort(1234)
                .build();

        Assert.assertTrue(VALIDATOR.validate(serviceSpec1, serviceSpec0).isEmpty());
        Assert.assertTrue(VALIDATOR.validate(serviceSpec1, serviceSpec1).isEmpty());
        Assert.assertFalse(VALIDATOR.validate(serviceSpec1, serviceSpec2).isEmpty());
        Assert.assertFalse(VALIDATOR.validate(serviceSpec1, serviceSpec3).isEmpty());


        Assert.assertTrue(VALIDATOR.validate(serviceSpec1, serviceSpecR01).isEmpty());
        Assert.assertFalse(VALIDATOR.validate(serviceSpec1, serviceSpecR12).isEmpty());
        Assert.assertFalse(VALIDATOR.validate(serviceSpec1, serviceSpecR13).isEmpty());
        Assert.assertFalse(VALIDATOR.validate(serviceSpec1, serviceSpecR23).isEmpty());

        Assert.assertTrue(VALIDATOR.validate(serviceSpec1, serviceSpecM01).isEmpty());
        Assert.assertFalse(VALIDATOR.validate(serviceSpec1, serviceSpecM12).isEmpty());
        Assert.assertFalse(VALIDATOR.validate(serviceSpec1, serviceSpecM13).isEmpty());
        Assert.assertFalse(VALIDATOR.validate(serviceSpec1, serviceSpecM23).isEmpty());
    }
}
