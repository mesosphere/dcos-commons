package com.mesosphere.sdk.storage;

import java.util.*;

import com.mesosphere.sdk.curator.CuratorUtils;
import com.mesosphere.sdk.scheduler.SchedulerUtils;
import com.mesosphere.sdk.state.ConfigStore;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.storage.StorageError.Reason;

/**
 * Utilities relating to usage of {@link Persister}s.
 */
public class PersisterUtils {

    /**
     * Path used for per-service namespaces in upstream storage.
     *
     * Service-namespaced data is stored under "Services/[namespace]/..."
     */
    private static final String SERVICE_NAMESPACE_ROOT_NAME = "Services";
    /**
     * Path used to create a backup of entire framework zdata
     */
    private static final String BACKUP_ROOT_NAME = "backup";

    private PersisterUtils() {
        // do not instantiate
    }

    /**
     * The path delimiter used in all Persister implementations.
     */
    public static final char PATH_DELIM = '/';

    /**
     * String representation of {@link #PATH_DELIM}.
     */
    public static final String PATH_DELIM_STR = String.valueOf(PATH_DELIM);


    /**
     * Returns all {@link StateStore} namespaces listed in the provided {@link Persister}, or an empty collection if
     * none could be found.
     *
     * @param persister the persister to be scanned
     * @throws PersisterException if there's a storage error other than data not found
     */
    public static Collection<String> fetchServiceNamespaces(Persister persister) throws PersisterException {
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
     * @throws IllegalArgumentException if the provided namespace is an empty string
     * @return {@code Services/[namespace]}
     */
    public static String getServiceNamespacedRoot(String namespace) {
        if (namespace.isEmpty()) {
            throw new IllegalArgumentException("Expected non-empty namespace");
        }
        return join(SERVICE_NAMESPACE_ROOT_NAME, SchedulerUtils.withEscapedSlashes(namespace));
    }

    /**
     * Returns a namespaced path or non-namespaced path, depending on the provided namespace value.
     *
     * @param namespace the namespace to use, or an empty string if no namespace is applicable
     * @param pathName the path to be namespaced (or not)
     * @return {@code Services/[namespace]/pathName}, or {@code pathName}
     */
    public static String getServiceNamespacedRootPath(String namespace, String pathName) {
        return namespace.isEmpty() ? pathName : join(getServiceNamespacedRoot(namespace), pathName);
    }

    /**
     * Combines the provided path elements into a unified path, autocorrecting for any delimiters within the elements.
     *
     * @param paths Arbitrary number of paths to be combined
     * @return All the paths combined, with any duplicate slashes cleaned up
     */
    public static String join(final String... paths) {
        return Arrays.stream(paths).reduce("", (first, second) -> {
            if (first.endsWith(PATH_DELIM_STR) && second.startsWith(PATH_DELIM_STR)) {
                // "hello/" + "/world" => "hello/world"
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
        for (int i = 0; i + 1 < elements.length; ++i) { // skip last element, if any
            if (elements[i].isEmpty()) { // Omit empty elements with eg "/" or "//" inputs
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
     * Returns all data present within the provided {@link Persister} in a flat map, omitting any stub parent entries
     * with {@code null} data.
     *
     * @throws PersisterException if the underlying {@link Persister} couldn't be accessed
     */
    public static Map<String, byte[]> getAllData(Persister persister) throws PersisterException {
        return getAllDataUnder(persister, PATH_DELIM_STR);
    }

    /**
     * Returns all data present within the provided {@link Persister}, under the provided path.
     */
    private static Map<String, byte[]> getAllDataUnder(Persister persister, String path) throws PersisterException {
        Map<String, byte[]> allData = new TreeMap<>(); // consistent ordering (mainly for tests)
        for (String child : persister.getChildren(path)) {
            String childPath = join(path, child);
            byte[] data = persister.get(childPath);
            // omit empty parents which lack data of their own:
            if (data != null) {
                allData.put(childPath, data);
            }
            allData.putAll(getAllDataUnder(persister, childPath)); // RECURSE
        }
        return allData;
    }

    /**
     * Returns a complete list of all keys present within the provided {@link Persister} in a flat list, including stub
     * parent entries which may lack data.
     *
     * @throws PersisterException if the underlying {@link Persister} couldn't be accessed
     */
    public static Collection<String> getAllKeys(Persister persister) throws PersisterException {
        return getAllKeysUnder(persister, PATH_DELIM_STR);
    }

    /**
     * Returns a complete list of all keys present within the provided {@link Persister}, under the provided path.
     */
    private static Collection<String> getAllKeysUnder(Persister persister, String path) throws PersisterException {
        Collection<String> allKeys = new TreeSet<>(); // consistent ordering (mainly for tests)
        for (String child : persister.getChildren(path)) {
            String childPath = join(path, child);
            allKeys.add(childPath);
            allKeys.addAll(getAllKeysUnder(persister, childPath)); // RECURSE
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
            if (e.getReason() == Reason.NOT_FOUND) {
                // Nothing to delete, apparently. Treat as a no-op
            } else {
                throw e;
            }
        }
    }

    private static void clearBackUp(Persister persister) throws PersisterException {
        // TODO add revisions to backup
        persister.recursiveDelete(BACKUP_ROOT_NAME);
    }

    public static void backUpFrameworkZKData(Persister persister) throws PersisterException {
        clearBackUp(persister);
        // We create a znode named `backup` (drop previous if exists) and copy framework znodes in to the backup znode
        persister.recursiveCopy(
                ConfigStore.getConfigurationsPathName(),
                join(BACKUP_ROOT_NAME, ConfigStore.getConfigurationsPathName())
        );
        persister.recursiveCopy(
                ConfigStore.getTargetIdPathName(),
                join(BACKUP_ROOT_NAME, ConfigStore.getTargetIdPathName())
        );
        persister.recursiveCopy(
                StateStore.getPropertiesRootName(),
                join(BACKUP_ROOT_NAME, StateStore.getPropertiesRootName())
        );
        persister.recursiveCopy(
                StateStore.getTasksRootName(),
                join(BACKUP_ROOT_NAME, StateStore.getTasksRootName())
        );
    }

    public static void migrateMonoToMultiZKData(Persister persister) throws PersisterException {
        /*
         * This is what we need to do to migrate to multi mode:
         * - Create a znode named `Services`
         *   - Create its child named {dcos_service_name}
         *   - Move the [ConfigTarget , Configurations , Properties, Tasks] nodes from
         *     top level nodes to be children of above child {dcos_service_name}
         * - Delete all the top level nodes : [ConfigTarget , Configurations , Properties, Tasks]
         */

        String serviceName = new String(persister.get(CuratorUtils.getServiceNameNode()));
        persister.recursiveCopy(
                ConfigStore.getConfigurationsPathName(),
                join(SERVICE_NAMESPACE_ROOT_NAME, serviceName, ConfigStore.getConfigurationsPathName())
        );
        persister.recursiveCopy(
                ConfigStore.getTargetIdPathName(),
                join(SERVICE_NAMESPACE_ROOT_NAME, serviceName, ConfigStore.getTargetIdPathName())
        );
        persister.recursiveCopy(
                StateStore.getPropertiesRootName(),
                join(SERVICE_NAMESPACE_ROOT_NAME, serviceName, StateStore.getPropertiesRootName())
        );
        persister.recursiveCopy(
                StateStore.getTasksRootName(),
                join(SERVICE_NAMESPACE_ROOT_NAME, serviceName, StateStore.getTasksRootName())
        );
        persister.recursiveDelete(ConfigStore.getConfigurationsPathName());
        persister.recursiveDelete(ConfigStore.getTargetIdPathName());
        persister.recursiveDelete(StateStore.getPropertiesRootName());
        persister.recursiveDelete(StateStore.getTasksRootName());
    }
}
