package com.mesosphere.sdk.storage;

import java.util.ArrayList;
import java.util.List;

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
}
