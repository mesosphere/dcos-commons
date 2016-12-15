
package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.config.validate.ConfigurationValidationError;
import com.mesosphere.sdk.config.validate.ConfigurationValidator;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.PodSpec;
import com.mesosphere.sdk.specification.ResourceSpecification;
import com.mesosphere.sdk.specification.ServiceSpec;
import com.mesosphere.sdk.specification.TaskSpec;
import org.apache.mesos.Protos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

class MasterTransportPortCannotChange implements ConfigurationValidator<ServiceSpec> {

    @Override
    public Collection<ConfigurationValidationError> validate(ServiceSpec nullableConfig, ServiceSpec newConfig) {
        List<ConfigurationValidationError> errors = new ArrayList<>();
        if (nullableConfig == null) {
            return errors;
        }
        int currentMasterTransportPort = getMasterTransportPort(nullableConfig);
        int newMasterTransportPort = getMasterTransportPort(newConfig);
        if (newMasterTransportPort != currentMasterTransportPort) {
            ConfigurationValidationError error = ConfigurationValidationError.transitionError("TaskSet[master]",
                    Integer.toString(currentMasterTransportPort),
                    Integer.toString(newMasterTransportPort),
                    String.format("New config's master node TaskSet has a different transport port: %s. Expected %s.",
                            newMasterTransportPort, currentMasterTransportPort));
            errors.add(error);
        }
        return errors;
    }

    private int getMasterTransportPort(ServiceSpec serviceSpec) {
        TaskSpec taskSpec = getFirstMasterTaskSpec(serviceSpec);
        List<Protos.Value.Range> rangeList = getPortRangeList(taskSpec);
        return (int) rangeList.get(1).getBegin();
    }

    private TaskSpec getFirstMasterTaskSpec(ServiceSpec serviceSpec) {
        PodSpec podSpec = serviceSpec.getPods().stream().
                filter(ts -> ts.getType().equals("master")).findFirst().orElse(null);
        return podSpec.getTasks().get(0);
    }

    private List<Protos.Value.Range> getPortRangeList(TaskSpec taskSpec) {
        Collection<ResourceSpecification> resources = taskSpec.getResourceSet().getResources();
        Map<String, ResourceSpecification> resourceMap = TaskUtils.getResourceSpecMap(resources);
        return resourceMap.get("ports").getValue().getRanges().getRangeList();
    }

}
