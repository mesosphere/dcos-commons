package com.mesosphere.sdk.cassandra.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Seed provider resource.
 */
@Path("/v1/seeds")
public class SeedsResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeedsResource.class);

    private final Set<String> configuredSeeds;

    public SeedsResource(Collection<String> configuredSeeds) {
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
        return new Seeds(new ArrayList<>(configuredSeeds));
    }
}
