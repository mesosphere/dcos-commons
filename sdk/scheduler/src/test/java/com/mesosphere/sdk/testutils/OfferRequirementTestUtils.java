package com.mesosphere.sdk.testutils;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.ExecutorRequirement;
import com.mesosphere.sdk.offer.NamedVIPRequirement;
import com.mesosphere.sdk.offer.PortRequirement;
import com.mesosphere.sdk.offer.ResourceRequirement;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.TaskRequirement;
import com.mesosphere.sdk.offer.VolumeRequirement;
import com.mesosphere.sdk.offer.evaluate.PortsRequirement;
import org.apache.commons.lang.StringUtils;
import org.apache.mesos.Protos;
import org.apache.mesos.Protos.HealthCheck;
import org.apache.mesos.Protos.TaskInfo;

import com.mesosphere.sdk.offer.InvalidRequirementException;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.evaluate.placement.PlacementRule;
import com.mesosphere.sdk.offer.taskdata.SchedulerLabelWriter;
import com.mesosphere.sdk.scheduler.SchedulerFlags;

import java.util.*;

/**
 * This class provides utility methods for tests concerned with OfferRequirements.
 */
public class OfferRequirementTestUtils {

    private OfferRequirementTestUtils() {
        // do not instantiate
    }

    public static OfferRequirement getOfferRequirement() {
        try {
            return getOfferRequirement(ResourceTestUtils.getUnreservedCpu(1.0));
        } catch (InvalidRequirementException e) {
            throw new IllegalStateException(e);
        }
    }

    public static OfferRequirement getOfferRequirement(
            Protos.Resource resource, Protos.Resource executorResource) throws InvalidRequirementException {
        TaskRequirement taskRequirement = new TaskRequirement(
                TaskTestUtils.getTaskInfo(Arrays.asList(resource)), getResourceRequirements(Arrays.asList(resource)));
        ExecutorRequirement executorRequirement = ExecutorRequirement.create(
                StringUtils.isEmpty(ResourceUtils.getResourceId(executorResource)) ?
                        TaskTestUtils.getExecutorInfo(executorResource) :
                        TaskTestUtils.getExistingExecutorInfo(executorResource),
                getResourceRequirements(Arrays.asList(executorResource)));

        return new OfferRequirement(
                TestConstants.TASK_TYPE,
                0,
                Arrays.asList(taskRequirement),
                Optional.of(executorRequirement),
                Optional.empty());
    }

    public static OfferRequirement getOfferRequirement(Protos.Resource resource) throws InvalidRequirementException {
        return getOfferRequirement(Arrays.asList(resource), false);
    }

    public static OfferRequirement getOfferRequirement(
            Protos.Resource resource, PlacementRule placementRule) throws InvalidRequirementException {
        return getOfferRequirement(Arrays.asList(resource), placementRule, false);
    }

    public static OfferRequirement getOfferRequirement(
            List<Protos.Resource> resources, boolean multipleTasks) throws InvalidRequirementException {
        return getOfferRequirement(resources, null, multipleTasks);
    }

    public static OfferRequirement getOfferRequirement(
            List<Protos.Resource> resources,
            PlacementRule placementRule,
            boolean multipleTasks) throws InvalidRequirementException {
        Collection<TaskRequirement> taskRequirements;
        if (multipleTasks) {
            taskRequirements = new ArrayList<>();
            for (int i = 0; i < resources.size(); ++i) {
                Protos.TaskInfo.Builder taskBuilder = TaskTestUtils.getTaskInfo(resources.get(i)).toBuilder();
                taskBuilder.setName(getIndexedName(taskBuilder.getName(), i));
                taskBuilder.setTaskId(CommonIdUtils.toTaskId(taskBuilder.getName()));
                taskRequirements.add(new TaskRequirement(
                        taskBuilder.build(), getResourceRequirements(Arrays.asList(resources.get(i)))));
            }
        } else {
            taskRequirements = Arrays.asList(new TaskRequirement(
                    TaskTestUtils.getTaskInfo(mergePorts(resources)), getResourceRequirements(resources)));
        }

        return new OfferRequirement(
                TestConstants.TASK_TYPE,
                0,
                taskRequirements,
                Optional.empty(),
                Optional.ofNullable(placementRule));

    }

    private static List<Protos.Resource> mergePorts(Collection<Protos.Resource> resources) {
        Protos.Resource.Builder ports = null;
        List<Protos.Resource> mergedResources = new ArrayList<>();
        for (Protos.Resource r : resources) {
            if (r.getName().equals(Constants.PORTS_RESOURCE_TYPE)) {
                ports = ports == null ? r.toBuilder() : ports.mergeRanges(r.getRanges());
            } else {
                mergedResources.add(r);
            }
        }

        if (ports != null) {
            mergedResources.add(ports.build());
        }

        return mergedResources;
    }

    public static Collection<ResourceRequirement> getResourceRequirements(Collection<Protos.Resource> resources) {
        Collection<ResourceRequirement> resourceRequirements = new ArrayList<>();
        Collection<ResourceRequirement> portRequirements = new ArrayList<>();
        int numPorts = 0;
        int numVips = 0;
        for (Protos.Resource resource : resources) {
            switch (resource.getName()) {
                case Constants.PORTS_RESOURCE_TYPE:
                    int port = (int) resource.getRanges().getRange(0).getBegin();
                    if (ResourceUtils.getLabel(resource, TestConstants.HAS_VIP_LABEL) == null) {
                        portRequirements.add(new PortRequirement(
                                resource,
                                "port-name-" + numPorts,
                                port,
                                Optional.of(getIndexedName(TestConstants.PORT_ENV_NAME, numPorts))));
                    } else {
                        resource = ResourceUtils.removeLabel(resource, TestConstants.HAS_VIP_LABEL);
                        portRequirements.add(new NamedVIPRequirement(
                                resource,
                                "port-name-" + numPorts,
                                port,
                                Optional.of(getIndexedName(TestConstants.PORT_ENV_NAME, numPorts)),
                                TestConstants.VIP_PROTOCOL,
                                TestConstants.VIP_VISIBILITY,
                                getIndexedName(TestConstants.VIP_NAME, numVips),
                                TestConstants.VIP_PORT + numVips));
                        ++numVips;
                    }
                    ++numPorts;
                    break;
                case Constants.DISK_RESOURCE_TYPE:
                    String containerPath = ResourceUtils.getLabel(resource, TestConstants.CONTAINER_PATH_LABEL);
                    if (containerPath != null) {
                        Protos.Resource.Builder builder = resource.toBuilder();
                        builder.getDiskBuilder().getVolumeBuilder().setContainerPath(containerPath);
                        resource = ResourceUtils.removeLabel(builder.build(), TestConstants.CONTAINER_PATH_LABEL);
                    }
                    resourceRequirements.add(new VolumeRequirement(resource));
                    break;
                default:
                    resourceRequirements.add(new ResourceRequirement(resource));
                    break;
            }
        }

        if (!portRequirements.isEmpty()) {
            resourceRequirements.add(new PortsRequirement(portRequirements));
        }

        return resourceRequirements;
    }

    public static Optional<HealthCheck> getReadinessCheck(TaskInfo taskInfo) throws TaskException {
        return new SchedulerLabelWriter(taskInfo) {
            @Override
            public Optional<HealthCheck> getReadinessCheck() throws TaskException {
                return super.getReadinessCheck();
            }
        }.getReadinessCheck();
    }

    private static String getIndexedName(String baseName, int index) {
        return index == 0 ? baseName : baseName + index;
    }

    public static SchedulerFlags getTestSchedulerFlags() {
        Map<String, String> map = new HashMap<>();
        map.put("PORT_API", String.valueOf(TestConstants.PORT_API_VALUE));
        map.put("EXECUTOR_URI", "test-executor-uri");
        map.put("JAVA_URI", "test-java-uri");
        map.put("LIBMESOS_URI", "test-libmesos-uri");
        return SchedulerFlags.fromMap(map);
    }
}
