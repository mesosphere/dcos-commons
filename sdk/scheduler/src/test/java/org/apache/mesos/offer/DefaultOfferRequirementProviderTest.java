package org.apache.mesos.offer;

import org.apache.mesos.offer.constrain.PlacementRule;
import org.apache.mesos.specification.*;
import org.apache.mesos.testutils.ResourceTestUtils;
import org.apache.mesos.testutils.TaskTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.config.DefaultTaskConfigRouter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This class tests the DefaultOfferRequirementProvider.
 */
public class DefaultOfferRequirementProviderTest {
    private static final double CPU = 1.0;
    private static final double MEM = 1024.0;
    private static final PlacementRule ALLOW_ALL = new PlacementRule() {
        @Override
        public Offer filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
            return offer;
        }
    };
    private static final DefaultOfferRequirementProvider PROVIDER =
            new DefaultOfferRequirementProvider(new DefaultTaskConfigRouter(), UUID.randomUUID());

    private EnvironmentVariables environmentVariables;

    @Mock
    private TaskSpecification mockTaskSpecification;

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        environmentVariables = new EnvironmentVariables();
        environmentVariables.set("EXECUTOR_URI", "");
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
        Assert.assertTrue(offerRequirement.getPlacementRuleOptional().isPresent());
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

    @Test(expected=InvalidRequirementException.class)
    public void testNewOfferRequirementEmptyResourceSets() throws InvalidRequirementException {
        PodSpec podSpec = mock(PodSpec.class);
        when(podSpec.getResources()).thenReturn(Collections.emptyList());

        TaskSpec taskSpec = mock(TaskSpec.class);
        when(taskSpec.getName()).thenReturn("task_spec_name");
        when(taskSpec.getPod()).thenReturn(podSpec);

        PROVIDER.getNewOfferRequirement(taskSpec);
    }

    @Test
    public void testNewOfferRequirement() throws InvalidRequirementException {
        ResourceSpecification resourceSpecification = new DefaultResourceSpecification(
                "cpus",
                ValueUtils.getValue(ResourceTestUtils.getDesiredCpu(1.0)),
                TestConstants.ROLE,
                TestConstants.PRINCIPAL);

        ResourceSet resourceSet = mock(ResourceSet.class);
        when(resourceSet.getResources()).thenReturn(Arrays.asList(resourceSpecification));
        when(resourceSet.getId()).thenReturn(TestConstants.RESOURCE_SET_ID);

        PodSpec podSpec = mock(PodSpec.class);
        when(podSpec.getResources()).thenReturn(Arrays.asList(resourceSet));
        when(podSpec.getType()).thenReturn(TestConstants.TASK_TYPE);

        HealthCheckSpec healthCheckSpec = mock(HealthCheckSpec.class);
        when(healthCheckSpec.getCommand()).thenReturn("./health-check-cmd");
        when(healthCheckSpec.getMaxConsecutiveFailures()).thenReturn(3);
        when(healthCheckSpec.getDelay()).thenReturn(Duration.ZERO);
        when(healthCheckSpec.getInterval()).thenReturn(Duration.ZERO);
        when(healthCheckSpec.getTimeout()).thenReturn(Duration.ZERO);
        when(healthCheckSpec.getGracePeriod()).thenReturn(Duration.ZERO);

        TaskSpec taskSpec = mock(TaskSpec.class);
        when(taskSpec.getName()).thenReturn("task_spec_name");
        when(taskSpec.getPod()).thenReturn(podSpec);
        when(taskSpec.getResourceSetId()).thenReturn(TestConstants.RESOURCE_SET_ID);
        when(taskSpec.getCommand()).thenReturn(Optional.of("./task-cmd"));
        when(taskSpec.getContainer()).thenReturn(Optional.empty());
        when(taskSpec.getHealthCheck()).thenReturn(Optional.of(healthCheckSpec));

        OfferRequirement offerRequirement = PROVIDER.getNewOfferRequirement(taskSpec);
        Assert.assertNotNull(offerRequirement);
        Assert.assertNotNull(offerRequirement.getTaskType());
    }

    private TaskSpecification setupMock(Protos.TaskInfo taskInfo) {
        return setupMock(taskInfo, Optional.empty());
    }

    private TaskSpecification setupMock(Protos.TaskInfo taskInfo, Optional<PlacementRule> placement) {
        when(mockTaskSpecification.getName()).thenReturn(taskInfo.getName());
        when(mockTaskSpecification.getCommand()).thenReturn(Optional.of(taskInfo.getCommand()));
        when(mockTaskSpecification.getContainer()).thenReturn(Optional.of(taskInfo.getContainer()));
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
