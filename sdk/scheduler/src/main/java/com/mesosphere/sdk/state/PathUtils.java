package com.mesosphere.sdk.state;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilities relating to path manipulation in state storage.
 */
public class PathUtils {

    private PathUtils() {
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
            // "hello/" + "/world"
            return new StringBuilder(first)
                    .deleteCharAt(first.length() - 1)
                    .append(second)
                    .toString();
        } else if (first.endsWith(PATH_DELIM) || second.startsWith(PATH_DELIM)) {
            // "hello/" + "world", or "hello" + "/world"
            return new StringBuilder(first)
                    .append(second)
                    .toString();
        } else {
            // "hello" + "world"
            return new StringBuilder(first)
                    .append(PATH_DELIM)
                    .append(second)
                    .toString();
        }
    }

    public static List<String> getParentPaths(final String path) {
        // /path/to/thing => ["/path", "/path/to"] (skip "/" and "/path/to/thing")
        List<String> paths = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        String[] elements = path.split(PATH_DELIM);
        for (int i = 0; i + 1 < elements.length; ++i) { // skip last element, if any
            if (elements[i].isEmpty()) {
                continue;
            }
            if (i == 0) {
                // Only include a "/" prefix if the input had a "/" prefix:
                if (path.startsWith("/")) {
                    builder.append(PATH_DELIM);
                }
            } else {
                builder.append(PATH_DELIM);
            }
            builder.append(elements[i]);
            paths.add(builder.toString());
        }
        return paths;
    }
}
