package com.mesosphere.sdk.elastic.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.config.validate.ConfigurationValidationError;
import org.apache.mesos.config.validate.ConfigurationValidator;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.specification.TaskSet;
import org.apache.mesos.specification.TaskSpecification;

import java.util.*;

import static com.mesosphere.sdk.elastic.scheduler.Elastic.MASTER_NODE_TYPE_NAME;

class MasterTransportPortCannotChange implements ConfigurationValidator<ServiceSpecification> {

    @Override
    public Collection<ConfigurationValidationError> validate(ServiceSpecification nullableConfig,
                                                             ServiceSpecification newConfig) {
        List<ConfigurationValidationError> errors = new ArrayList<>();
        if (nullableConfig == null) {
            return errors;
        }
        String currentMasterTransportPort = getMasterTransportPort(nullableConfig);
        String newMasterTransportPort = getMasterTransportPort(newConfig);
        if (newMasterTransportPort == null || !newMasterTransportPort.equals(currentMasterTransportPort)) {
            ConfigurationValidationError error = ConfigurationValidationError.transitionError("TaskSet[master]",
                    currentMasterTransportPort,
                    newMasterTransportPort,
                    String.format("New config's master node TaskSet has a different transport port: %s. Expected %s.",
                            newMasterTransportPort, currentMasterTransportPort));
            errors.add(error);
        }
        return errors;
    }

    private String getMasterTransportPort(ServiceSpecification serviceSpecification) {
        TaskSet taskSet = serviceSpecification.getTaskSets().stream().
                filter(ts -> ts.getName().equals(MASTER_NODE_TYPE_NAME)).findFirst().orElse(null);
        TaskSpecification taskSpecification = taskSet.getTaskSpecifications().get(0);
        Optional<Protos.CommandInfo> commandInfo = taskSpecification.getCommand();
        if (!commandInfo.isPresent()) {
            return null;
        }
        Protos.Environment environment = commandInfo.get().getEnvironment();
        Map<String, String> envFromTask = TaskUtils.fromEnvironmentToMap(environment);

        return envFromTask.get("MASTER_NODE_TRANSPORT_PORT");
    }


}
