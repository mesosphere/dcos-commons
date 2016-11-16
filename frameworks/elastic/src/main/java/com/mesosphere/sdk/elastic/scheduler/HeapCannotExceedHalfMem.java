package com.mesosphere.sdk.elastic.scheduler;

import org.apache.mesos.Protos;
import org.apache.mesos.config.validate.ConfigurationValidationError;
import org.apache.mesos.config.validate.ConfigurationValidator;
import org.apache.mesos.offer.TaskUtils;
import org.apache.mesos.specification.ServiceSpecification;
import org.apache.mesos.specification.TaskSet;
import org.apache.mesos.specification.TaskSpecification;

import java.util.*;

import static com.mesosphere.sdk.elastic.scheduler.Elastic.KIBANA_TYPE_NAME;

class HeapCannotExceedHalfMem implements ConfigurationValidator<ServiceSpecification> {

    @Override
    public Collection<ConfigurationValidationError> validate(ServiceSpecification nullableConfig,
                                                             ServiceSpecification newConfig) {
        List<ConfigurationValidationError> errors = new ArrayList<>();
        Map<String, String> envFromTask;

        for (TaskSet taskSet : newConfig.getTaskSets()) {
            if (taskSet.getName().equals(KIBANA_TYPE_NAME)) {
                continue;
            }
            TaskSpecification taskSpecification = taskSet.getTaskSpecifications().get(0);
            Optional<Protos.CommandInfo> commandInfo = taskSpecification.getCommand();
            if (!commandInfo.isPresent()) {
                continue;
            }
            Protos.Environment environment = commandInfo.get().getEnvironment();
            envFromTask = TaskUtils.fromEnvironmentToMap(environment);
            int taskHeap = OfferUtils.heapFromOpts(envFromTask.get("ES_JAVA_OPTS"));
            int mem = TaskUtils.getMemory(taskSpecification);
            if (heapExceedsHalfMem(taskHeap, mem)) {
                String format = "Elasticsearch %s node heap size %d exceeds half memory size %d in Service '%s'";
                String message = String.format(format, taskSet.getName(), taskHeap, mem, newConfig.getName());
                errors.add(ConfigurationValidationError.valueError("JVM Heap", taskSet.getName(), message));
            }
        }
        return errors;
    }

    private boolean heapExceedsHalfMem(int taskHeap, int mem) {
        return taskHeap > (mem / 2);
    }


}
