package com.mesosphere.sdk.cassandra.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seed provider resource.
 */
@Path("/v1/seeds")
public class SeedsResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeedsResource.class);
    private static final String DATACENTER_PROPERTY_KEY = "datacenter_";

    private final StateStore stateStore;
    private final Set<String> configuredDatacenters;
    private final int configuredSeedCount;

    public SeedsResource(StateStore stateStore, List<String> configuredDatacenters, int configuredSeedCount) {
        this.stateStore = stateStore;
        this.configuredDatacenters = new HashSet<>(configuredDatacenters);
        this.configuredSeedCount = configuredSeedCount;
    }

    @GET
    @Produces("application/json")
    public Response seeds() {
        try {
            return Response.ok(getSeeds(), MediaType.APPLICATION_JSON).build();
        } catch (Exception e) {
            LOGGER.error("Failed to retrieve seeds: {}", e);
            return Response.serverError().build();
        }
    }

    @PUT
    @Path("/datacenters/{datacenterName}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addRemoteSeeds(
            @PathParam("datacenterName") String datacenterName,
            String jsonBody) {
        if (!configuredDatacenters.contains(datacenterName)) {
            LOGGER.error(
                    "Data center {} is not one of the configured remote datacenters: {}",
                    datacenterName, configuredDatacenters);
            return Response.serverError().build();
        }

        String key = getDatacenterKey(datacenterName);
        try {
            SerializationUtils.fromJsonString(jsonBody, Seeds.class);
        } catch (IOException e) {
            LOGGER.error("Couldn't get seeds from body {}: {}", jsonBody, e);
            return Response.serverError().build();
        }

        stateStore.storeProperty(key, jsonBody.getBytes(StandardCharsets.UTF_8));
        return Response.ok("", MediaType.TEXT_PLAIN).build();
    }

    private Seeds getSeeds() {
        List<String> seeds = getLocalSeeds();
        boolean isSeed = seeds.size() < configuredSeedCount;

        seeds.addAll(getRemoteSeeds());
        return new Seeds(seeds, isSeed);
    }

    private List<String> getLocalSeeds() {
        return stateStore.fetchTasks().stream()
                .filter(t -> {
                    Optional<Protos.TaskStatus> status = stateStore.fetchStatus(t.getName());
                    return status.isPresent() && status.get().getState().equals(Protos.TaskState.TASK_RUNNING);
                })
                .filter(t -> t.getName().endsWith("server"))
                .map(t -> TaskUtils.getLabel("offer_hostname", t))
                .filter(h -> h.isPresent())
                .map(h -> {
                    try {
                        return InetAddress.getByName(h.get()).getHostAddress();
                    } catch (UnknownHostException e) {
                        return null;
                    }
                })
                .filter(s -> s != null)
                .limit(configuredSeedCount)
                .collect(Collectors.toList());
    }

    private List<String> getRemoteSeeds() {
        List<String> remoteSeeds = new ArrayList<>();

        for (String datacenter : getDatacenters()) {
            remoteSeeds.addAll(getDatacenterSeeds(datacenter));
        }

        return remoteSeeds;
    }

    private Collection<String> getDatacenters() {
        Collection<String> datacenters = new ArrayList<>();

        for (String k : stateStore.fetchPropertyKeys()) {
            if (k.startsWith(DATACENTER_PROPERTY_KEY)) {
                datacenters.add(k);
            }
        }

        return datacenters;
    }

    private static String getDatacenterKey(String datacenterName) {
        return DATACENTER_PROPERTY_KEY + datacenterName;
    }

    private List<String> getDatacenterSeeds(String datacenter) {
        Seeds seeds;

        try {
            seeds = SerializationUtils.fromJsonString(
                    new String(stateStore.fetchProperty(datacenter), StandardCharsets.UTF_8), Seeds.class);
        } catch (IOException e) {
            LOGGER.warn("Failed to retrieve seeds from datacenter {} from state store: {}", datacenter, e);
            return Collections.emptyList();
        }

        return seeds.getSeeds();
    }

    private class Seeds {
        @JsonProperty("seeds")
        private final List<String> seeds;
        @JsonProperty("is_seed")
        private final boolean isSeed;

        @JsonCreator
        public Seeds(@JsonProperty("seeds") List<String> seeds, @JsonProperty("is_seed") boolean isSeed) {
            this.seeds = seeds;
            this.isSeed = isSeed;
        }

        @JsonIgnore
        public List<String> getSeeds() {
            return seeds;
        }

        @JsonIgnore
        public boolean isSeed() {
            return isSeed;
        }
    }
}
