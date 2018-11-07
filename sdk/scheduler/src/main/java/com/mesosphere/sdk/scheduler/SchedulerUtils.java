package com.mesosphere.sdk.scheduler;

import com.mesosphere.sdk.storage.PersisterUtils;

/**
 * This class provides utilities common to the construction and operation of Mesos Schedulers.
 */
public final class SchedulerUtils {

  /**
   * Escape sequence to use for slashes in service names. Slashes are used in DC/OS for folders, and we don't want to
   * confuse ZK with those.
   */
  private static final String SLASH_REPLACEMENT = "__";

  private SchedulerUtils() {
  }

  /**
   * Removes any slashes from the provided name and replaces them with double underscores. Any leading slash is
   * removed entirely. This is useful for sanitizing framework names, framework roles, and curator paths.
   * <p>
   * For example:
   * <ul>
   * <li>/path/to/kafka => path__to__kafka</li>
   * <li>path/to/some-kafka => path__to__some-kafka</li>
   * <li>path__to__kafka => EXCEPTION</li>
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
}
