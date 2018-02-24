package com.mesosphere.sdk.scheduler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NamespacedApiServlet extends HttpServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(NamespacedApiServlet.class);
    private static final String SERVICE_PATH_PREFIX = "/runs";
    private static final String SERVICE_PATH_PREFIX_SLASH = SERVICE_PATH_PREFIX + "/";

    private final Object lock = new Object();
    private final Map<String, HttpServlet> namespaces = new HashMap<>();

    public void addServlet(String namespace, HttpServlet servlet) {
        synchronized (lock) {
            namespaces.put(namespace, servlet);
        }
    }

    public void removeServlet(String namespace) {
        synchronized (lock) {
            HttpServlet removed = namespaces.remove(namespace);
            if (removed == null) {
                LOGGER.warn("Unable to find HTTP namespace '{}', known entries are:\n{}",
                        namespace, new TreeSet<>(namespaces.keySet()));
            }
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        synchronized (lock) {
            HttpServlet servlet = getNearestServlet(req.getPathInfo());

            if (servlet == null) {
                // Output a basic 404 page:
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.setContentType("text/html");
                final OutputStream output = resp.getOutputStream();
                StringBuilder sb = new StringBuilder();
                sb.append("<html><head><title>404 Not Found: ");
                sb.append(req.getPathInfo());
                sb.append("</title></head>" +
                        "<body><h2>404 Not Found: ");
                sb.append(req.getPathInfo());
                sb.append("</h2></body></html>");
                output.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                output.flush();
            }

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("text/plain");
            final OutputStream output = resp.getOutputStream();
            output.write(("hi" + req.getPathInfo()).getBytes(StandardCharsets.UTF_8));
            output.flush();
            return;
/*
            if (req.getPathInfo().startsWith(SERVICE_PATH_PREFIX_SLASH)) {
                // When forwarding this request, we want to omit the namespaced prefix.
                // For example, if we get...: /namespaced/path/to/foo
                // ... then the servlet gets: /path/to/foo
                servlet.service(new PrefixRemovedRequest(req, SERVICE_PATH_PREFIX), resp);
            } else {
                servlet.service(req, resp);
            }*/
        }
    }

    /**
     * Returns the best matching servlet for the specified path, or null if no matching servlet is found.
     * For example, given a query of "/path/to/bar", and namespaces of "", "path", "path/to", and "path/to/foo", the
     * best match is "path/to".
     *
     * @param httpPath an HTTP path starting with a slash
     */
    private HttpServlet getNearestServlet(String httpPath) {
        LOGGER.info("Finding {} in {}", httpPath, new TreeSet<>(namespaces.keySet()));
        String pathToCheck = httpPath.substring(1); // Remove leading slash
        // Given "request/path/to/thing", try finding the best matching namespace:
        // - request/path/to/thing
        // - request/path/to
        // - request/path
        // - request
        // - ''
        while (true) {
            // See if this path has a matching namespace:
            HttpServlet match = namespaces.get(pathToCheck);
            if (match != null) {
                LOGGER.info("MATCH: {}", pathToCheck);
                return match;
            }

            // Get parent of this path:
            int lastSlash = pathToCheck.lastIndexOf('/');
            if (lastSlash == -1) {
                LOGGER.info("NO MATCH FOUND");
                break;
            }
            pathToCheck = pathToCheck.substring(0, lastSlash);
            LOGGER.info("try {}", pathToCheck);
        }

        // Try for a root namespace
        return namespaces.get("");
    }

    private static class PrefixRemovedRequest extends HttpServletRequestWrapper {
        private final String path;

        private PrefixRemovedRequest(HttpServletRequest request, String prefix) {
            super(request);
            String origPath = request.getPathInfo();
            if (!origPath.startsWith(prefix)) {
                throw new IllegalArgumentException(String.format("%s does not start with %s", origPath, prefix));
            }
            this.path = origPath.substring(prefix.length());
            LOGGER.info("Treating request {} as {}", origPath, this.path);
        }

        @Override
        public String getPathInfo() {
            return path;
        }
    }
}
