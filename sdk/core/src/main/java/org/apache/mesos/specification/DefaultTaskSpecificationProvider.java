package org.apache.mesos.specification;

import java.util.UUID;

import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.config.ConfigStore;
import org.apache.mesos.config.ConfigStoreException;
import org.apache.mesos.offer.TaskException;
import org.apache.mesos.offer.TaskUtils;

/**
 * Default implementation of {@link TaskSpecificationProvider}. This version retrieves the
 * {@link TaskSpecification} data from a {@link ConfigStore} which stores
 * {@link ServiceSpecification}s.
 */
public class DefaultTaskSpecificationProvider implements TaskSpecificationProvider {

    private final ConfigStore<ServiceSpecification> configStore;

    public DefaultTaskSpecificationProvider(ConfigStore<ServiceSpecification> configStore) {
        this.configStore = configStore;
    }

    @Override
    public TaskSpecification getTaskSpecification(TaskInfo taskInfo) throws TaskException {
        UUID configId = TaskUtils.getTargetConfiguration(taskInfo);
        ServiceSpecification serviceSpec;
        try {
            serviceSpec = configStore.fetch(configId);
        } catch (ConfigStoreException e) {
            throw new TaskException(String.format(
                    "Unable to retrieve ServiceSpecification ID %s referenced by TaskInfo[%s]",
                    configId, taskInfo.getName()), e);
        }
        TaskSpecification taskSpec = TaskUtils.getTaskSpecification(serviceSpec, taskInfo);
        if (taskSpec == null) {
            throw new TaskException(String.format(
                    "No TaskSpecification found for TaskInfo[%s]", taskInfo.getName()));
        }
        return taskSpec;
    }
}
