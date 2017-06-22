package com.mesosphere.sdk.cassandra.api;

import com.mesosphere.sdk.api.ResponseUtils;
import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Seed provider resource.
 */
@Path("/v1/seeds")
public class SeedsResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeedsResource.class);
    private static final String SEEDS_PROPERTY_PREFIX = "remote_seeds_";

    private final int configuredSeedCount;
    private final StateStore stateStore;

    public SeedsResource(StateStore stateStore, int configuredSeedCount) {
        this.stateStore = stateStore;
        this.configuredSeedCount = configuredSeedCount;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response seeds() {
        try {
            return ResponseUtils.plainOkResponse(SerializationUtils.toJsonString(getSeeds()));
        } catch (Exception e) {
            LOGGER.error("Failed to retrieve seeds: {}", e);
            return ResponseUtils.plainResponse("Failed to retrieve seeds", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Path("/register/{datacenter}")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response registerRemoteSeeds(@PathParam("datacenter") String datacenter, Seeds remoteSeeds) {
        try {
            stateStore.storeProperty(
                    getPropertyName(datacenter),
                    SerializationUtils.toJsonString(remoteSeeds).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.error("Couldn't serialize seeds from request: {}", e);
            return ResponseUtils.plainResponse(
                    "Couldn't serialize seeds from request", Response.Status.INTERNAL_SERVER_ERROR);
        }

        return ResponseUtils.plainOkResponse("");
    }

    private Seeds getSeeds() {
        final Set<String> seeds = new HashSet<>(getLocalSeeds());
        final boolean isSeed = seeds.size() < configuredSeedCount;

        seeds.addAll(getRemoteSeeds());

        return new Seeds(new ArrayList<>(seeds), isSeed);
    }

    private Set<String> getLocalSeeds() {
        return stateStore.fetchTaskNames().stream()
                .map(stateStore::fetchStatus)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(s ->
                        s.getState() == Protos.TaskState.TASK_RUNNING &&
                        s.hasContainerStatus() &&
                        s.getContainerStatus().getNetworkInfosCount() > 0)
                .map(s -> s.getContainerStatus().getNetworkInfosList().get(0).getIpAddresses(0).getIpAddress())
                .limit(configuredSeedCount)
                .collect(Collectors.toSet());
    }

    private Set<String> getRemoteSeeds() {
        return stateStore.fetchPropertyKeys().stream()
                .filter(this::isRemoteSeedsProperty)
                .map(k -> {
                    try {
                        return SerializationUtils.fromJsonString(
                                        new String(stateStore.fetchProperty(k),
                                        StandardCharsets.UTF_8), Seeds.class)
                                .getSeeds().stream();
                    } catch (IOException e) {
                        LOGGER.error("Couldn't deserialize stored seeds from datacenter {}", getDataCenterName(k));
                        return Stream.<String>empty();
                    }
                })
                .flatMap(Function.identity())
                .collect(Collectors.toSet());
    }

    private boolean isRemoteSeedsProperty(String property) {
        return property.startsWith(SEEDS_PROPERTY_PREFIX);
    }

    private String getDataCenterName(String property) {
        return property.replace(SEEDS_PROPERTY_PREFIX, "");
    }

    private String getPropertyName(String datacenter) {
        return SEEDS_PROPERTY_PREFIX + datacenter;
    }
}
