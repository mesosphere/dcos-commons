package com.mesosphere.sdk.config.validate;

import com.mesosphere.sdk.specification.*;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskVolumesCannotChangeTest {
    private static final int DISK_SIZE_MB = 1000;

    private static final ConfigValidator<ServiceSpec> VALIDATOR = new TaskVolumesCannotChange();

    // slight differences between volumes:
    private static final VolumeSpecification VOLUME1 = new DefaultVolumeSpecification(
            DISK_SIZE_MB, VolumeSpecification.Type.MOUNT, "/path/to/volume", "role", "principal", "VOLUME");
    private static final VolumeSpecification VOLUME2 = new DefaultVolumeSpecification(
            DISK_SIZE_MB + 3, VolumeSpecification.Type.MOUNT, "/path/to/volume", "role", "principal", "VOLUME");
    private static final VolumeSpecification VOLUME3 = new DefaultVolumeSpecification(
            DISK_SIZE_MB, VolumeSpecification.Type.ROOT, "/path/to/volume", "role", "principal", "VOLUME");

    @Mock private PodSpec mockPodSpec1;
    @Mock private PodSpec mockPodSpec2;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    /*
    @Test
    public void testUnchangedVolumes() throws InvalidRequirementException {
        ServiceSpec serviceSpec1 = DefaultServiceSpec.Builder.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1))
                .build();

        ServiceSpec serviceSpec2 = DefaultServiceSpec.Builder.newBuilder()
                .name("svc1")
                .role(TestConstants.ROLE)
                .principal(TestConstants.PRINCIPAL)
                .pods(Arrays.asList(mockPodSpec1, mockPodSpec2))
                .build();

        ServiceSpecification serviceSpec3 = new DefaultServiceSpecification(
                "svc3",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockPodSpec1, mockPodSpec2)),
                        DefaultTaskSet.create("set2", Arrays.asList(mockTaskSpec3))));
        ServiceSpecification serviceSpec4 = new DefaultServiceSpecification(
                "svc4",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockPodSpec1)),
                        DefaultTaskSet.create("set2", Arrays.asList(mockPodSpec2, mockTaskSpec3))));

        when(mockPodSpec1.getName()).thenReturn("task1");
        when(mockPodSpec2.getName()).thenReturn("task2");
        when(mockTaskSpec3.getName()).thenReturn("task3");
        when(mockPodSpec1.getVolumes()).thenReturn(Arrays.asList(VOLUME1));
        when(mockPodSpec2.getVolumes()).thenReturn(Arrays.asList(VOLUME2, VOLUME3));
        when(mockTaskSpec3.getVolumes()).thenReturn(Collections.emptyList());

        Assert.assertTrue(VALIDATOR.validate(serviceSpec1, serviceSpec1).isEmpty());
        Assert.assertTrue(VALIDATOR.validate(serviceSpec2, serviceSpec2).isEmpty());
        Assert.assertTrue(VALIDATOR.validate(serviceSpec3, serviceSpec3).isEmpty());
        Assert.assertTrue(VALIDATOR.validate(serviceSpec4, serviceSpec4).isEmpty());

        // the tasks are reshuffled, but each task's volumes don't change!:
        Assert.assertTrue(VALIDATOR.validate(serviceSpec1, serviceSpec2).isEmpty());
        Assert.assertTrue(VALIDATOR.validate(serviceSpec1, serviceSpec3).isEmpty());
        Assert.assertTrue(VALIDATOR.validate(serviceSpec1, serviceSpec4).isEmpty());
        Assert.assertTrue(VALIDATOR.validate(serviceSpec2, serviceSpec1).isEmpty());
        Assert.assertTrue(VALIDATOR.validate(serviceSpec2, serviceSpec3).isEmpty());
        Assert.assertTrue(VALIDATOR.validate(serviceSpec2, serviceSpec4).isEmpty());
        Assert.assertTrue(VALIDATOR.validate(serviceSpec3, serviceSpec1).isEmpty());
        Assert.assertTrue(VALIDATOR.validate(serviceSpec3, serviceSpec2).isEmpty());
        Assert.assertTrue(VALIDATOR.validate(serviceSpec3, serviceSpec4).isEmpty());
        Assert.assertTrue(VALIDATOR.validate(serviceSpec4, serviceSpec1).isEmpty());
        Assert.assertTrue(VALIDATOR.validate(serviceSpec4, serviceSpec2).isEmpty());
        Assert.assertTrue(VALIDATOR.validate(serviceSpec4, serviceSpec3).isEmpty());
    }

    @Test
    public void testChangedVolumes() throws InvalidRequirementException {

        // "Change" only detected when mockPodSpec1 AND mockPodSpec2 are compared.

        ServiceSpecification serviceSpec1 = new DefaultServiceSpecification(
                "svc1",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockPodSpec1))));
        ServiceSpecification serviceSpec2 = new DefaultServiceSpecification(
                "svc2",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockPodSpec2))));
        ServiceSpecification serviceSpec13 = new DefaultServiceSpecification(
                "svc3",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockPodSpec1)),
                        DefaultTaskSet.create("set2", Arrays.asList(mockTaskSpec3))));
        ServiceSpecification serviceSpec23 = new DefaultServiceSpecification(
                "svc4",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockPodSpec2)),
                        DefaultTaskSet.create("set2", Arrays.asList(mockTaskSpec3))));

        when(mockPodSpec1.getName()).thenReturn("task1");
        when(mockPodSpec2.getName()).thenReturn("task1");
        when(mockTaskSpec3.getName()).thenReturn("task2");
        when(mockPodSpec1.getVolumes()).thenReturn(Arrays.asList(VOLUME1));
        when(mockPodSpec2.getVolumes()).thenReturn(Arrays.asList(VOLUME2, VOLUME3));
        when(mockTaskSpec3.getVolumes()).thenReturn(Collections.emptyList());

        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec1, serviceSpec2).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec1, serviceSpec13).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec1, serviceSpec23).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec2, serviceSpec1).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec2, serviceSpec13).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec2, serviceSpec23).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec13, serviceSpec1).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec13, serviceSpec2).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec13, serviceSpec23).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec23, serviceSpec1).size());
        Assert.assertEquals(0, VALIDATOR.validate(serviceSpec23, serviceSpec2).size());
        Assert.assertEquals(1, VALIDATOR.validate(serviceSpec23, serviceSpec13).size());
    }

    @Test
    public void testDuplicateTasksInService() throws InvalidRequirementException {
        ServiceSpecification serviceSpec1 = new DefaultServiceSpecification(
                "svc1",
                Arrays.asList(
                        DefaultTaskSet.create("set1", Arrays.asList(mockPodSpec1, mockPodSpec2))));

        when(mockPodSpec1.getName()).thenReturn("task1");
        when(mockPodSpec2.getName()).thenReturn("task1");

        // error hit twice, once in 'each' servicespec:
        Assert.assertEquals(2, VALIDATOR.validate(serviceSpec1, serviceSpec1).size());
    }
    */
}
