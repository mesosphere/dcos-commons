package org.apache.mesos.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.config.DefaultTaskConfigRouter;
import org.apache.mesos.offer.constrain.PlacementRule;
import org.apache.mesos.specification.*;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.testutils.ResourceTestUtils;
import org.apache.mesos.testutils.TaskTestUtils;
import org.apache.mesos.testutils.TestConstants;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

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

    private static DefaultOfferRequirementProvider PROVIDER;
    private EnvironmentVariables environmentVariables;

    @Mock private TaskSpecification mockTaskSpecification;
    @Mock private StateStore stateStore;

    @Mock private PodSpec podSpec;
    @Mock private PodInstance podInstance;
    @Mock private HealthCheckSpec healthCheckSpec;
    @Mock private CommandSpec commandSpec;
    @Mock private TaskSpec taskSpec;
    @Mock private ResourceSet resourceSet;

    private ResourceSpecification resourceSpecification = new DefaultResourceSpecification(
            "cpus",
            ValueUtils.getValue(ResourceTestUtils.getDesiredCpu(1.0)),
            TestConstants.ROLE,
            TestConstants.PRINCIPAL,
            "CPUS");

    @Before
    public void beforeEach() {
        MockitoAnnotations.initMocks(this);
        environmentVariables = new EnvironmentVariables();
        environmentVariables.set("EXECUTOR_URI", "");

        when(podSpec.getResources()).thenReturn(Arrays.asList(resourceSet));
        when(podSpec.getType()).thenReturn(TestConstants.POD_TYPE);
        when(podSpec.getUser()).thenReturn(Optional.empty());

        when(podInstance.getPod()).thenReturn(podSpec);
        when(podInstance.getIndex()).thenReturn(0);

        when(healthCheckSpec.getCommand()).thenReturn(TestConstants.HEALTH_CHECK_CMD);
        when(healthCheckSpec.getMaxConsecutiveFailures()).thenReturn(3);
        when(healthCheckSpec.getDelay()).thenReturn(0);
        when(healthCheckSpec.getInterval()).thenReturn(0);
        when(healthCheckSpec.getTimeout()).thenReturn(0);
        when(healthCheckSpec.getGracePeriod()).thenReturn(0);

        when(commandSpec.getValue()).thenReturn(TestConstants.TASK_CMD);

        when(taskSpec.getName()).thenReturn(TestConstants.TASK_NAME);
        when(taskSpec.getType()).thenReturn(TestConstants.POD_TYPE);
        when(taskSpec.getResourceSet()).thenReturn(resourceSet);
        when(taskSpec.getCommand()).thenReturn(Optional.of(commandSpec));
        when(taskSpec.getContainer()).thenReturn(Optional.empty());
        when(taskSpec.getHealthCheck()).thenReturn(Optional.of(healthCheckSpec));
        when(taskSpec.getGoal()).thenReturn(TaskSpec.GoalState.RUNNING);

        when(resourceSet.getResources()).thenReturn(Arrays.asList(resourceSpecification));
        when(resourceSet.getId()).thenReturn(TestConstants.RESOURCE_SET_ID);

        when(podSpec.getTasks()).thenReturn(Arrays.asList(taskSpec));
        when(podSpec.getResources()).thenReturn(Arrays.asList(resourceSet));

        PROVIDER = new DefaultOfferRequirementProvider(new DefaultTaskConfigRouter(), stateStore, UUID.randomUUID());
    }

    /*
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
        when(podSpec.getType()).thenReturn(TestConstants.POD_TYPE);
        when(podSpec.getUser()).thenReturn(Optional.empty());

        CommandSpec commandSpec = mock(CommandSpec.class);
        when(commandSpec.getValue()).thenReturn(TestConstants.TASK_CMD);

        TaskSpec taskSpec = mock(TaskSpec.class);
        when(taskSpec.getName()).thenReturn("task_spec_name");
        when(taskSpec.getPod()).thenReturn(podSpec);
        when(taskSpec.getCommand()).thenReturn(Optional.of(commandSpec));
        when(taskSpec.getGoal()).thenReturn(TaskSpec.GoalState.RUNNING);

        when(podSpec.getTasks()).thenReturn((Arrays.asList(taskSpec)));

        PROVIDER.getNewOfferRequirement(pod);
    }
    */

    @Test
    public void testNewOfferRequirement() throws InvalidRequirementException {
        OfferRequirement offerRequirement = PROVIDER.getNewOfferRequirement(podInstance);
        Assert.assertNotNull(offerRequirement);
        Assert.assertEquals(TestConstants.POD_TYPE, offerRequirement.getType());
        Assert.assertEquals(1, offerRequirement.getTaskRequirements().size());

        TaskRequirement taskRequirement = offerRequirement.getTaskRequirements().stream().findFirst().get();
        TaskInfo taskInfo = taskRequirement.getTaskInfo();
        Assert.assertEquals(TestConstants.TASK_CMD, taskInfo.getCommand().getValue());
        Assert.assertEquals(TestConstants.HEALTH_CHECK_CMD, taskInfo.getHealthCheck().getCommand().getValue());
        Assert.assertFalse(taskInfo.hasContainer());
    }

    @Test
    public void testExistingOfferRequirement() throws InvalidRequirementException {
        Protos.Resource cpu = ResourceTestUtils.getExpectedCpu(CPU);
        Protos.TaskInfo taskInfo = TaskTestUtils.getTaskInfo(Arrays.asList(cpu));
        String taskName = TaskSpec.getInstanceName(podInstance, podInstance.getPod().getTasks().get(0));
        when(stateStore.fetchTask(taskName)).thenReturn(Optional.of(taskInfo));
        OfferRequirement offerRequirement =
                PROVIDER.getExistingOfferRequirement(podInstance);
        Assert.assertNotNull(offerRequirement);
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
                                resource.getReservation().getPrincipal(),
                                resource.getName().toUpperCase()));
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
                                resource.getReservation().getPrincipal(),
                                resource.getName().toUpperCase()));
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
