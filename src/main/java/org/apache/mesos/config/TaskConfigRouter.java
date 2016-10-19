package org.apache.mesos.config;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.mesos.specification.TaskSet;

/**
 * Default implementation of a {@link Configuration} which provides built-in serialization to
 * persisted config storage, and automatic routing of config settings to tasks with a
 * per-{@link TaskSet} granularity.
 */
public class TaskConfigRouter {

    private static final String TASKCFG_ENV_FORMAT = "TASKCFG_%s_";
    private static final String ALL_TASKS_PREFIX = String.format(TASKCFG_ENV_FORMAT, "ALL");
    private static final Pattern ENVVAR_INVALID_CHARS = Pattern.compile("[^a-zA-Z0-9]");

    private final Map<String, String> config;

    /**
     * Creates a new config router which uses the current system environment for its configuration.
     *
     * @see System#getenv()
     */
    public TaskConfigRouter() {
        this(System.getenv());
    }

    /**
     * Creates a new config router which uses the provided configuration.
     */
    public TaskConfigRouter(Map<String, String> config) {
        this.config = config;
    }

    /**
     * Returns a {@link ConfigNamespace} for all configuration settings to inject into the provided
     * {@code taskSet}. If no matching data was found, returns a new empty {@link ConfigNamespace}.
     */
    public ConfigNamespace getConfig(TaskSet taskSet) {
        return getConfig(taskSet.getName());
    }

    /**
     * Returns a {@link ConfigNamespace} for all configuration settings to inject into the provided
     * {@code taskType}. If no matching data was found, returns a new empty {@link ConfigNamespace}.
     */
    public ConfigNamespace getConfig(String taskType) {
        return new ConfigNamespace(getPrefixesForTaskType(taskType), config);
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

    private static Set<String> getPrefixesForTaskType(String taskType) {
        Set<String> set = new HashSet<>();
        // convert eg "index-mgr" or "index.mgr" to "TASKCFG_INDEX_MGR_":
        String taskTypePrefix = String.format(
                TASKCFG_ENV_FORMAT,
                ENVVAR_INVALID_CHARS.matcher(taskType.toUpperCase()).replaceAll("_"));
        set.add(taskTypePrefix); // TASKCFG_<TASK_TYPE>_* for items matching this type
        set.add(ALL_TASKS_PREFIX); // TASKCFG_ALL_* for items matching all types
        return set;
    }
}
