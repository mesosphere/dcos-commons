package com.mesosphere.sdk.cassandra.api;

import com.mesosphere.sdk.offer.TaskUtils;
import com.mesosphere.sdk.state.StateStore;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seed provider resource.
 */
@Path("/v1/seeds")
public class SeedsResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeedsResource.class);

    private final StateStore stateStore;
    private final Set<String> configuredSeeds;

    public SeedsResource(StateStore stateStore, Collection<String> configuredSeeds) {
        this.stateStore = stateStore;
        this.configuredSeeds = new HashSet<>(configuredSeeds);
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

    private Seeds getSeeds() {
        return new Seeds(stateStore.fetchTasks().stream()
                .filter(t -> {
                    Optional<Protos.TaskStatus> status = stateStore.fetchStatus(t.getName());
                    return status.isPresent() && status.get().getState().equals(Protos.TaskState.TASK_RUNNING);
                })
                .filter(t -> configuredSeeds.contains(t.getName()))
                .map(t -> TaskUtils.getLabel("offer_hostname", t).get())
                .map(h -> {
                    try {
                        return InetAddress.getByName(h).getHostAddress();
                    } catch (UnknownHostException e) {
                        return null;
                    }
                })
                .filter(s -> s != null)
                .collect(Collectors.toList()));
    }
}
