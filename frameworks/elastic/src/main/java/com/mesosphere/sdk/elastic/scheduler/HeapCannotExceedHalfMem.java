package com.mesosphere.sdk.elastic.scheduler;

import com.mesosphere.sdk.config.validate.ConfigurationValidationError;
import com.mesosphere.sdk.config.validate.ConfigurationValidator;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.specification.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


class HeapCannotExceedHalfMem implements ConfigurationValidator<ServiceSpec> {

    private static int getMemory(TaskSpec taskSpec) {
        Collection<ResourceSpecification> resources = taskSpec.getResourceSet().getResources();
        Map<String, ResourceSpecification> resourceMap = TaskUtils.getResourceSpecMap(resources);
        return (int) resourceMap.get("mem").getValue().getScalar().getValue();
    }

    @Override
    public Collection<ConfigurationValidationError> validate(ServiceSpec nullableConfig, ServiceSpec newConfig) {
        List<ConfigurationValidationError> errors = new ArrayList<>();

        for (PodSpec podSpec : newConfig.getPods()) {
            if (podSpec.getType().equals("kibana")) {
                continue;
            }
            TaskSpec taskSpec = podSpec.getTasks().get(0);
            Optional<CommandSpec> commandSpec = taskSpec.getCommand();
            if (!commandSpec.isPresent()) {
                continue;
            }
            Map<String, String> environment = commandSpec.get().getEnvironment();
            int taskHeap = heapFromOpts(environment.get("ES_JAVA_OPTS"));
            int mem = getMemory(taskSpec);
            if (heapExceedsHalfMem(taskHeap, mem)) {
                String format = "Elasticsearch %s node heap size %d exceeds half memory size %d in Service '%s'";
                String message = String.format(format, podSpec.getType(), taskHeap, mem, newConfig.getName());
                errors.add(ConfigurationValidationError.valueError("JVM Heap", podSpec.getType(), message));
            }
        }
        return errors;
    }

    private boolean heapExceedsHalfMem(int taskHeap, int mem) {
        return taskHeap > (mem / 2);
    }

    private int heapFromOpts(String esHeapOpts) {
        Pattern pattern = Pattern.compile("-Xms(\\d+)M");
        Matcher matcher = pattern.matcher(esHeapOpts);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }


}
