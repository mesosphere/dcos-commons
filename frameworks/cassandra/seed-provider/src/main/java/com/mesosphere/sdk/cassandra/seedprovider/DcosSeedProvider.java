package com.mesosphere.sdk.cassandra.seedprovider;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.cassandra.locator.SeedProvider;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements the Cassandra SeedProvider interface, providing a list of seeds requested from the DC/OS Apache
 * Cassandra scheduler.
 */
public class DcosSeedProvider implements SeedProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(DcosSeedProvider.class);

    private final String schedulerURL;

    public DcosSeedProvider(final Map<String, String> properties) {
        schedulerURL = properties.get("scheduler_url");
    }

    @Override
    public List<InetAddress> getSeeds() {
        Optional<JSONObject> seedsResponse = getSchedulerSeeds(schedulerURL);

        if (!seedsResponse.isPresent()) {
            LOGGER.error("Failed to retrieve seedsResponse from scheduler");
            return Collections.emptyList();
        }

        boolean isSeed = (boolean) seedsResponse.get().get("is_seed");
        List<InetAddress> seeds = new ArrayList<>(
                ((List<String>) seedsResponse.get().get("seeds")).stream()
                        .map(a -> {
                            try {
                                return InetAddress.getByName(a);
                            } catch (UnknownHostException e) {
                                return null;
                            }
                        })
                        .filter(a -> a != null)
                        .collect(Collectors.toList()));

        if (isSeed) {
            try {
                seeds.add(getLocalAddress());
            } catch (UnknownHostException e) {
                LOGGER.error("Couldn't get local address: {}", e);
            }
        }

        return seeds;
    }

    private static InetAddress getLocalAddress() throws UnknownHostException {
        String address = System.getenv("MESOS_CONTAINER_IP");

        if (address == null || address.isEmpty()) {
            LOGGER.error("Could not resolve local IP from MESOS_CONTAINER_IP envvar, defaulting to localhost");
            return InetAddress.getLocalHost();
        }

        return InetAddress.getByName(address);
    }

    private static Optional<JSONObject> getSchedulerSeeds(String schedulerURLString) {
        HttpURLConnection schedulerConnection;

        try {
            schedulerConnection = getConnection(schedulerURLString);
        } catch (MalformedURLException e) {
            LOGGER.error("Scheduler URL is malformed, can't retrieve seeds: {}", schedulerURLString);
            return Optional.empty();
        } catch (IOException e) {
            LOGGER.error("Failed to reach scheduler: {}", e);
            return Optional.empty();
        }

        try {
            if (schedulerConnection.getResponseCode() != 200) {
                LOGGER.error("Request to get seeds from scheduler got HTTP error: {}",
                        schedulerConnection.getErrorStream().toString());
                return Optional.empty();
            }
        } catch (IOException e) {
            LOGGER.error("Request to get seeds from scheduler failed: {}", e);
            return Optional.empty();
        }

        String body;
        try {
            body = getRequestBody(schedulerConnection);
        } catch (IOException e) {
            LOGGER.error("Couldn't read request body: {}", e);
            return Optional.empty();
        }

        return Optional.ofNullable((JSONObject) JSONValue.parse(body));
    }

    private static HttpURLConnection getConnection(String url) throws IOException {
        URL remoteURL;
        HttpURLConnection connection;

        remoteURL = new URL(url);
        connection = (HttpURLConnection) remoteURL.openConnection();

        connection.setConnectTimeout(1000);
        connection.setReadTimeout(10000);
        connection.setRequestMethod("GET");

        return connection;
    }

    private static String getRequestBody(HttpURLConnection connection) throws IOException {
        DataInputStream responseStream = new DataInputStream((InputStream) connection.getContent());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        byte[] buffer = new byte[2048];
        int count = 0;
        while ((count = responseStream.read(buffer, 0, buffer.length)) != -1) {
            baos.write(buffer, 0, count);
        }

        responseStream.close();
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }
}
