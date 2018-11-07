package com.mesosphere.sdk.storage;

import com.mesosphere.sdk.framework.FrameworkConfig;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.SchedulerBuilder;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.SchemaVersionStore;
import com.mesosphere.sdk.state.SchemaVersionStore.SchemaVersion;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.StorageError.Reason;

import org.slf4j.Logger;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Utilities relating to usage of {@link Persister}s.
 */
public final class PersisterUtils {

  /**
   * The path delimiter used in all Persister implementations.
   */
  public static final char PATH_DELIM = '/';

  /**
   * String representation of {@link #PATH_DELIM}.
   */
  public static final String PATH_DELIM_STR = String.valueOf(PATH_DELIM);

  /**
   * Path used for per-service namespaces in upstream storage.
   * <p>
   * Service-namespaced data is stored under "Services/[namespace]/..."
   */
  private static final String SERVICE_NAMESPACE_ROOT_NAME = "Services";

  private static final Logger LOGGER = LoggingUtils.getLogger(PersisterUtils.class);

  private static final String ZNODE_TIME_STAMP_FORMAT = "yyyy-MM-dd-HHmmss";

  private PersisterUtils() {
    // do not instantiate
  }

  /**
   * Returns all {@link StateStore} namespaces listed in the provided {@link Persister}, or
   * an empty collection if none could be found.
   *
   * @param persister the persister to be scanned
   * @throws PersisterException if there's a storage error other than data not found
   */
  public static Collection<String> fetchServiceNamespaces(
      Persister persister)
      throws PersisterException
  {
    try {
      return persister.getChildren(SERVICE_NAMESPACE_ROOT_NAME);
    } catch (PersisterException e) {
      if (e.getReason() == Reason.NOT_FOUND) {
        // Persister currently lacks namespace storage.
        return Collections.emptySet();
      } else {
        throw e;
      }
    }
  }

  /**
   * Returns the root path for the provided namespace.
   *
   * @param namespace the namespace to use
   * @return {@code Services/[namespace]}
   * @throws IllegalArgumentException if the provided namespace is an empty string
   */
  public static String getServiceNamespacedRoot(String namespace) {
    if (namespace.isEmpty()) {
      throw new IllegalArgumentException("Expected non-empty namespace");
    }
    return joinPaths(SERVICE_NAMESPACE_ROOT_NAME, SchedulerUtils.withEscapedSlashes(namespace));
  }

  /**
   * Returns a namespaced path or non-namespaced path, depending on the provided namespace value.
   *
   * @param namespace the namespace to use, or an empty string if no namespace is applicable
   * @param pathName  the path to be namespaced (or not)
   * @return {@code Services/[namespace]/pathName}, or {@code pathName}
   */
  public static String getServiceNamespacedRootPath(String namespace, String pathName) {
    return namespace.isEmpty() ?
        pathName :
        joinPaths(getServiceNamespacedRoot(namespace), pathName);
  }

  /**
   * Combines the provided path elements into a unified path, autocorrecting for any delimiters
   * within the elements.
   *
   * @param paths Arbitrary number of paths to be combined
   * @return All the paths combined, with any duplicate slashes cleaned up
   */
  public static String joinPaths(final String... paths) {
    return Arrays.stream(paths).reduce("", (first, second) -> {
      if (first.isEmpty() || second.isEmpty()) {
        return first + second;
      } else if (first.endsWith(PATH_DELIM_STR) && second.startsWith(PATH_DELIM_STR)) {
        // "hello/" + "/world" => "hello/world"
        // "hello/", "//world" => "hello//world"
        return new StringBuilder(first).deleteCharAt(first.length() - 1).append(second).toString();
      } else if (first.endsWith(PATH_DELIM_STR) || second.startsWith(PATH_DELIM_STR)) {
        // "hello/" + "world", OR "hello" + "/world" => "hello/world"
        return first + second;
      } else {
        // "hello" + "world" => "hello/world"
        return first + PATH_DELIM + second;
      }
    });
  }

  /**
   * Returns a recursive list of all paths which are the parent of the provided path.
   *
   * <p>/path/to/thing => ["/path", "/path/to"] (skip "/" and "/path/to/thing")
   */
  public static List<String> getParentPaths(final String path) {
    List<String> paths = new ArrayList<>();
    StringBuilder builder = new StringBuilder();
    String[] elements = path.split(PATH_DELIM_STR);
    // skip last element, if any
    for (int i = 0; i + 1 < elements.length; ++i) {
      // Omit empty elements with eg "/" or "//" inputs
      if (elements[i].isEmpty()) {
        continue;
      }
      if (i != 0) {
        builder.append(PATH_DELIM);
      }
      builder.append(elements[i]);
      paths.add(builder.toString());
    }
    return paths;
  }

  /**
   * Returns all data present within the provided {@link Persister} in a flat map, omitting any
   * stub parent entries with {@code null} data.
   *
   * @throws PersisterException if the underlying {@link Persister} couldn't be accessed
   */
  public static Map<String, byte[]> getAllData(Persister persister) throws PersisterException {
    return getAllDataUnder(persister, PATH_DELIM_STR);
  }

  /**
   * Returns all data present within the provided {@link Persister}, under the provided path.
   */
  private static Map<String, byte[]> getAllDataUnder(
      Persister persister,
      String path)
      throws PersisterException
  {
    // consistent ordering (mainly for tests)
    Map<String, byte[]> allData = new TreeMap<>();
    for (String child : persister.getChildren(path)) {
      String childPath = joinPaths(path, child);
      byte[] data = persister.get(childPath);
      // omit empty parents which lack data of their own:
      if (data != null) {
        allData.put(childPath, data);
      }
      // RECURSE
      allData.putAll(getAllDataUnder(persister, childPath));
    }
    return allData;
  }

  /**
   * Returns a complete list of all keys present within the provided {@link Persister} in a flat
   * list, including stub parent entries which may lack data.
   *
   * @throws PersisterException if the underlying {@link Persister} couldn't be accessed
   */
  public static Collection<String> getAllKeys(Persister persister) throws PersisterException {
    return getAllKeysUnder(persister, PATH_DELIM_STR);
  }

  /**
   * Returns a complete list of all keys present within the provided {@link Persister},
   * under the provided path.
   */
  private static Collection<String> getAllKeysUnder(
      Persister persister,
      String path)
      throws PersisterException
  {
    // consistent ordering (mainly for tests)
    Collection<String> allKeys = new TreeSet<>();
    for (String child : persister.getChildren(path)) {
      String childPath = joinPaths(path, child);
      allKeys.add(childPath);
      // RECURSE
      allKeys.addAll(getAllKeysUnder(persister, childPath));
    }
    return allKeys;
  }

  /**
   * Deletes all data in the provided persister, or does nothing if the persister is already empty.
   */
  public static void clearAllData(Persister persister) throws PersisterException {
    try {
      persister.recursiveDelete(PersisterUtils.PATH_DELIM_STR);
    } catch (PersisterException e) {
      if (e.getReason() != Reason.NOT_FOUND) {
        throw e;
      } // Else : Nothing to delete, apparently. Treat as a no-op
    }
  }

  /**
   * Notes:
   * <p>
   * 1. We do not migrate in Dynamic Service Mode because if we do so, there is a "gap" between
   * the scheduler registering with mesos and it receiving inbound requests with ymls. During
   * this "gap" if it receives any offers, it does not know what to do with them if we support
   * migration. Hence we support migration only in static service mode.
   * 2. Note that if the {{user}} field in the yml is not specified in the mono mode, we used to
   * default to `root`. In the multi mode however, the framework config takes precedence if a user
   * is not mentioned. Caution must be taken to ensure the service user is same (mention it
   * explicitly in both mono and multi for the sake of consistency) during the migration.
   * 3. The migration MUST happen before the {@link SchedulerBuilder#build()} is called
   * (i.e., before the scheduler talks to {@link Persister}.
   */
  public static void checkAndMigrate(FrameworkConfig frameworkConfig, Persister persister) {
    LOGGER.info("Checking if migration is needed...");
    SchemaVersionStore schemaVersionStore = new SchemaVersionStore(persister);
    SchemaVersion curVer = schemaVersionStore.getOrSetVersion(SchemaVersion.MULTI_SERVICE);
    if (curVer == SchemaVersion.SINGLE_SERVICE) {
      try {
        LOGGER.info(
            "Found single-service schema in ZK Storage that can be migrated to multi-service schema"
        );
        // We create a znode named `backup-<timestamp>` (drop if exists) and copy the
        // framework znodes data
        String backupRoot = getTimestampedNodeName("backup");
        try {
          persister.recursiveDelete(backupRoot);
        } catch (PersisterException e) {
          if (e.getReason() != Reason.NOT_FOUND) {
            throw e;
          }
        }
        List<String> zkDataPathsForMigration = Arrays.asList(
            ConfigStore.getConfigurationsPathName(),
            ConfigStore.getTargetIdPathName(),
            StateStore.getPropertiesRootName(),
            StateStore.getTasksRootName()
        );
        for (String path : zkDataPathsForMigration) {
          persister.recursiveCopy(path, joinPaths(backupRoot, path));
        }
        /*
         * This is what we need to do to migrate to multi mode:
         * - Create a znode named `Services`
         *   - Create its child named {dcos_service_name}
         *   - Move the [ConfigTarget , Configurations , Properties, Tasks] nodes from
         *     top level nodes to be children of above child {dcos_service_name}
         * - Delete all the top level nodes : [ConfigTarget , Configurations , Properties, Tasks]
         */
        for (String path : zkDataPathsForMigration) {
          persister.recursiveCopy(path,
              getServiceNamespacedRootPath(frameworkConfig.getFrameworkName(), path));
        }
        try {
          for (String path : zkDataPathsForMigration) {
            persister.recursiveDelete(path);
          }
        } catch (PersisterException e) {
          LOGGER.error(
              "Delete the Mono Service Schema Manually. Ignoring the exception encountered " +
                  "when trying to delete the mono service schema nodes afer a s" +
                  "uccessful migration.",
              e
          );
        }
        schemaVersionStore.store(SchemaVersion.MULTI_SERVICE);
        LOGGER.info("Successfully migrated from single-service schema to multi-service schema");
      } catch (PersisterException e) {
        LOGGER.error("Unable to migrate ZK data : ", e);
        throw new RuntimeException(e);
      }
    } else if (curVer == SchemaVersion.MULTI_SERVICE) {
      LOGGER.info("Schema version matches that of multi service mode. Nothing to migrate.");
    } else {
      throw new IllegalStateException(String.format(
          "Storage schema version [%d] is not supported by this software. Expected either single " +
              "service schema version [%d] or multi service schema version [%d].",
          curVer.toInt(),
          SchemaVersion.SINGLE_SERVICE.toInt(),
          SchemaVersion.MULTI_SERVICE.toInt()
      ));
    }
  }

  private static String getTimestampedNodeName(String nodeName) {
    return String.format(
        "%s-%s",
        nodeName,
        new SimpleDateFormat(ZNODE_TIME_STAMP_FORMAT).format(new Date())
    );
  }
}
