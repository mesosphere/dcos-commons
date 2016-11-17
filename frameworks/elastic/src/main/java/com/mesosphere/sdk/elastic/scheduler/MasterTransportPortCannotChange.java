package com.mesosphere.sdk.elastic.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.config.validate.ConfigurationValidationError;
import org.apache.mesos.config.validate.ConfigurationValidator;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.specification.TaskSet;
import org.apache.mesos.specification.TaskSpecification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.mesosphere.sdk.elastic.scheduler.Elastic.MASTER_NODE_TYPE_NAME;

class MasterTransportPortCannotChange implements ConfigurationValidator<ServiceSpecification> {

    @Override
    public Collection<ConfigurationValidationError> validate(ServiceSpecification nullableConfig,
                                                             ServiceSpecification newConfig) {
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

    private int getMasterTransportPort(ServiceSpecification serviceSpecification) {
        TaskSet taskSet = serviceSpecification.getTaskSets().stream().
                filter(ts -> ts.getName().equals(MASTER_NODE_TYPE_NAME)).findFirst().orElse(null);
        TaskSpecification taskSpecification = taskSet.getTaskSpecifications().get(0);
        List<Protos.Value.Range> rangeList = TaskUtils.getPortRangeList(taskSpecification);
        return (int) rangeList.get(1).getBegin();
    }

}
