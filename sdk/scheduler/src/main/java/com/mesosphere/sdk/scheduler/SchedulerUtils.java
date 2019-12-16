package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.TaskException;
import com.mesosphere.sdk.offer.taskdata.TaskLabelReader;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.PersisterUtils;

import org.apache.mesos.Protos;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * This class provides utilities common to the construction and operation of Mesos Schedulers.
 */
public final class SchedulerUtils {

  /**
   * Escape sequence to use for slashes in service names. Slashes are used in DC/OS for folders, and we don't want to
   * confuse ZK with those.
   */
  public static final String SLASH_REPLACEMENT = "__";

  private static final Logger LOGGER = LoggingUtils.getLogger(SchedulerUtils.class);

  private SchedulerUtils() {
  }

  /**
   * Removes any slashes from the provided name and replaces them with double underscores. Any leading slash is
   * removed entirely. This is useful for sanitizing framework names, framework roles, and curator paths.
   * <p>
   * For example:
   * <ul>
   * <li>/path/to/service => path__to__service</li>
   * <li>path/to/some-service => path__to__some-service</li>
   * <li>path__to__service => EXCEPTION</li>
   * </ul>
   *
   * @throws IllegalArgumentException if the provided name already contains double underscores
   */
  public static String withEscapedSlashes(String name) {
    if (name.contains(SLASH_REPLACEMENT)) {
      throw new IllegalArgumentException(
          "Service names may not contain double underscores: " + name
      );
    }

    String result = name;
    if (name.startsWith(PersisterUtils.PATH_DELIM_STR)) {
      // Trim any leading slash
      result = name.substring(PersisterUtils.PATH_DELIM_STR.length());
    }

    // Replace any other slashes (e.g. from folder support) with double underscores:
    return result.replace(PersisterUtils.PATH_DELIM_STR, SLASH_REPLACEMENT);
  }


  /**
   * Returns a grouped mapping of agent hostname to resource ids present on that agent.
   * <p>
   * The resulting map will be sorted alphabetically by agent hostname, and the resource ids within each agent entry
   * will also be sorted alphabetically.
   */
  public static Map<String, Set<String>> getResourceIdsByAgentHost(StateStore stateStore) {
    Collection<Protos.TaskInfo> allTasks = stateStore.fetchTasks();
    Set<String> taskIdsInErrorState = stateStore.fetchStatuses().stream()
        .filter(taskStatus -> taskStatus.getState().equals(Protos.TaskState.TASK_ERROR))
        .map(taskStatus -> taskStatus.getTaskId().getValue())
        .collect(Collectors.toSet());

    // Filter the tasks to those that have actually created resources. Tasks in an ERROR state which are also
    // flagged as permanently failed are assumed to not have resources reserved on Mesos' end, despite our State
    // Store still listing them with resources. This is because we log the planned reservation before it occurs.
    Collection<Protos.TaskInfo> tasksWithExpectedReservations = allTasks.stream()
        .filter(taskInfo -> !(FailureUtils.isPermanentlyFailed(taskInfo)
            && taskIdsInErrorState.contains(taskInfo.getTaskId().getValue())))
        .collect(Collectors.toList());

    // The agent hostname mapping is sorted alphabetically. This doesn't affect functionality and is just for user
    // experience when viewing the uninstall plan.
    Map<String, Set<String>> resourceIdsByAgentHost = new TreeMap<>();
    for (Protos.TaskInfo taskInfo : tasksWithExpectedReservations) {
      String hostname;
      try {
        hostname = new TaskLabelReader(taskInfo).getHostname();
      } catch (TaskException e) {
        LOGGER.warn(
            String.format("Failed to determine hostname of task %s", taskInfo.getName()),
            e
        );
        hostname = "UNKNOWN_AGENT";
      }

      // Sort the resource ids alphabetically within each agent. This doesn't affect functionality and is just
      // for user experience when viewing the uninstall plan.
      resourceIdsByAgentHost
          .computeIfAbsent(hostname, k -> new TreeSet<>())
          .addAll(ResourceUtils.getResourceIds(ResourceUtils.getAllResources(taskInfo)));
    }

    LOGGER.info("Detected {}/{} tasks across {} agents",
        tasksWithExpectedReservations.size(), allTasks.size(), resourceIdsByAgentHost.size());

    return resourceIdsByAgentHost;
  }
}
