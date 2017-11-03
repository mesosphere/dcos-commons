package com.mesosphere.sdk.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;

import com.mesosphere.sdk.api.types.TaskInfoAndStatus;

/**
 * Utilities for handling HTTP requests from clients.
 */
public class RequestUtils {

    private RequestUtils() {
        // do not instantiate
    }

    /**
     * Parses a JSON list payload in the form of a string, returning the corresponding Java list.
     *
     * @throws JSONException if the provided string could not be parsed as a JSON list
     */
    public static List<String> parseJsonList(String payload) throws JSONException {
        if (StringUtils.isBlank(payload)) {
            return Collections.emptyList();
        }
        List<String> strings = new ArrayList<>();
        Iterator<Object> iter = new JSONArray(payload).iterator();
        while (iter.hasNext()) {
            strings.add(iter.next().toString());
        }
        return strings;
    }

    /**
     * Returns a new list which contains only the tasks for a given pod which matched the provided task names.
     * For example, a task named "foo" in "pod-0" will be returned if the filter contains either "foo" or "pod-0-foo".
     */
    public static Collection<TaskInfoAndStatus> filterPodTasks(
            String podName, Collection<TaskInfoAndStatus> podTasks, Set<String> taskNameFilter) {
        if (taskNameFilter.isEmpty()) {
            return podTasks;
        }
        // given a task named "foo" in "pod-0", allow either "foo" or "pod-0-foo" to match:
        final Set<String> prefixedTaskNameFilter = taskNameFilter.stream()
                .map(filterEntry -> String.format("%s-%s", podName, filterEntry))
                .collect(Collectors.toSet());
        List<TaskInfoAndStatus> filteredPodTasks = podTasks.stream()
                .filter(task ->
                        prefixedTaskNameFilter.contains(task.getInfo().getName()) ||
                        taskNameFilter.contains(task.getInfo().getName()))
                .collect(Collectors.toList());
        return filteredPodTasks;
    }
}
