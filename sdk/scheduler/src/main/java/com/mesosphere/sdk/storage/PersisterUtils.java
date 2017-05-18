package com.mesosphere.sdk.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Utilities relating to usage of {@link Persister}s.
 */
public class PersisterUtils {

    private PersisterUtils() {
        // do not instantiate
    }

    /**
     * The path delimiter used in all Persister implementations.
     */
    public static final String PATH_DELIM = "/";

    /**
     * Combines the provided path elements into a unified path, autocorrecting for any delimiters within the elements.
     */
    public static String join(final String first, final String second) {
        if (first.endsWith(PATH_DELIM) && second.startsWith(PATH_DELIM)) {
            // "hello/" + "/world" => "hello/world"
            return new StringBuilder(first).deleteCharAt(first.length() - 1).append(second).toString();
        } else if (first.endsWith(PATH_DELIM) || second.startsWith(PATH_DELIM)) {
            // "hello/" + "world", OR "hello" + "/world" => "hello/world"
            return new StringBuilder(first).append(second).toString();
        } else {
            // "hello" + "world" => "hello/world"
            return new StringBuilder(first).append(PATH_DELIM).append(second).toString();
        }
    }

    /**
     * Returns a recursive list of all paths which are the parent of the provided path.
     *
     * <p>/path/to/thing => ["/path", "/path/to"] (skip "/" and "/path/to/thing")
     */
    public static List<String> getParentPaths(final String path) {
        List<String> paths = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        String[] elements = path.split(PATH_DELIM);
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
        return getAllDataUnder(persister, PATH_DELIM);
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
        return getAllKeysUnder(persister, PATH_DELIM);
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
}
