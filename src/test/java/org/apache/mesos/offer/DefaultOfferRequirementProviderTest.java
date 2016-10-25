package org.apache.mesos.offer;

import org.apache.mesos.offer.constrain.PassthroughGenerator;
import org.apache.mesos.offer.constrain.PassthroughRule;
import org.apache.mesos.offer.constrain.PlacementRuleGenerator;
import org.apache.mesos.specification.DefaultResourceSpecification;
import org.apache.mesos.specification.DefaultVolumeSpecification;
import org.apache.mesos.specification.ResourceSpecification;
import org.apache.mesos.specification.TaskSpecification;
import org.apache.mesos.specification.VolumeSpecification;
import org.apache.mesos.testutils.ResourceTestUtils;
import org.apache.mesos.testutils.TaskTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.config.DefaultTaskConfigRouter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;

/**
 * This class tests the DefaultOfferRequirementProvider.
 */
public class DefaultOfferRequirementProviderTest {
    private static final double CPU = 1.0;
    private static final double MEM = 1024.0;
    private static final PlacementRuleGenerator ALLOW_ALL = new PassthroughGenerator(new PassthroughRule("test"));
    private static final DefaultOfferRequirementProvider PROVIDER =
            new DefaultOfferRequirementProvider(new DefaultTaskConfigRouter(), UUID.randomUUID());

    @Mock
    private TaskSpecification mockTaskSpecification;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testPlacementPassthru() throws InvalidRequirementException {
        Protos.Resource cpu = ResourceTestUtils.getExpectedCpu(CPU);
        Protos.TaskInfo taskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(cpu));

        TaskSpecification taskSpecification = setupMock(taskInfo, Optional.of(ALLOW_ALL));

        OfferRequirement offerRequirement =
                PROVIDER.getExistingOfferRequirement(taskInfo, taskSpecification);
        Assert.assertNotNull(offerRequirement);
        Assert.assertFalse(offerRequirement.getPersistenceIds().contains(TestConstants.PERSISTENCE_ID));
        Assert.assertTrue(offerRequirement.getResourceIds().contains(TestConstants.RESOURCE_ID));
        Assert.assertTrue(offerRequirement.getPlacementRuleGeneratorOptional().isPresent());
    }

    @Test
    public void testAddNewDesiredResource() throws InvalidRequirementException {
        Protos.Resource cpu = ResourceTestUtils.getExpectedCpu(CPU);
        Protos.Resource mem = ResourceTestUtils.getDesiredMem(MEM);
        Protos.TaskInfo taskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(cpu));

        // Add memory requirement to the new TaskSpecification
        TaskSpecification taskSpecification = setupMock(taskInfo.toBuilder().addResources(mem).build());

        OfferRequirement offerRequirement =
                PROVIDER.getExistingOfferRequirement(taskInfo, taskSpecification);
        Assert.assertNotNull(offerRequirement);
        Assert.assertFalse(offerRequirement.getPersistenceIds().contains(TestConstants.PERSISTENCE_ID));
        Assert.assertTrue(offerRequirement.getResourceIds().contains(TestConstants.RESOURCE_ID));
    }

    private TaskSpecification setupMock(Protos.TaskInfo taskInfo) {
        return setupMock(taskInfo, Optional.empty());
    }

    private TaskSpecification setupMock(Protos.TaskInfo taskInfo, Optional<PlacementRuleGenerator> placement) {
        when(mockTaskSpecification.getName()).thenReturn(taskInfo.getName());
        when(mockTaskSpecification.getCommand()).thenReturn(taskInfo.getCommand());
        when(mockTaskSpecification.getHealthCheck()).thenReturn(Optional.empty());
        when(mockTaskSpecification.getResources()).thenReturn(getResources(taskInfo));
        when(mockTaskSpecification.getVolumes()).thenReturn(getVolumes(taskInfo));
        when(mockTaskSpecification.getConfigFiles()).thenReturn(Collections.emptyList());
        when(mockTaskSpecification.getPlacement()).thenReturn(placement);
        return mockTaskSpecification;
    }

    private static Collection<ResourceSpecification> getResources(Protos.TaskInfo taskInfo) {
        Collection<ResourceSpecification> resourceSpecifications = new ArrayList<>();
        for (Protos.Resource resource : taskInfo.getResourcesList()) {
            if (!resource.hasDisk()) {
                resourceSpecifications.add(
                        new DefaultResourceSpecification(
                                resource.getName(),
                                ValueUtils.getValue(resource),
                                resource.getRole(),
                                resource.getReservation().getPrincipal()));
            }
        }
        return resourceSpecifications;
    }

    private static Collection<VolumeSpecification> getVolumes(Protos.TaskInfo taskInfo) {
        Collection<VolumeSpecification> volumeSpecifications = new ArrayList<>();
        for (Protos.Resource resource : taskInfo.getResourcesList()) {
            if (resource.hasDisk()) {
                volumeSpecifications.add(
                        new DefaultVolumeSpecification(
                                resource.getScalar().getValue(),
                                getVolumeType(resource.getDisk()),
                                resource.getDisk().getVolume().getContainerPath(),
                                resource.getRole(),
                                resource.getReservation().getPrincipal()));
            }
        }

        return volumeSpecifications;
    }

    private static VolumeSpecification.Type getVolumeType(Protos.Resource.DiskInfo diskInfo) {
        if (diskInfo.hasSource()) {
            Protos.Resource.DiskInfo.Source.Type type = diskInfo.getSource().getType();
            switch (type) {
                case MOUNT:
                    return VolumeSpecification.Type.MOUNT;
                case PATH:
                    return VolumeSpecification.Type.PATH;
                default:
                    throw new IllegalArgumentException("unexpected type: " + type);
            }
        } else {
            return VolumeSpecification.Type.ROOT;
        }
    }
}
