package com.mesosphere.sdk.kafka.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;

import com.mesosphere.sdk.api.ResponseUtils;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.util.Optional;

/**
 *  Broker Resource.
 */
@Path("/v1/brokers")
@Produces("application/json")
public class BrokerResource {
    private final Logger log = LoggerFactory.getLogger(BrokerResource.class);
    private KafkaZKClient kafkaZkClient;

    public BrokerResource(KafkaZKClient kafkaZkClient) {
        this.kafkaZkClient = kafkaZkClient;
    }

    @GET
    public Response listBrokers() {
        try {
            return ResponseUtils.jsonOkResponse(kafkaZkClient.listBrokers());
        } catch (Exception ex) {
            log.error("Failed to fetch broker ids with exception:", ex);
            return Response.serverError().build();
        }
    }

    @GET
    @Path("/{id}")
    public Response getBroker(@PathParam("id") String id) {
        try {
            Optional<JSONObject> optionalBroker = kafkaZkClient.getBroker(id);
            if (!optionalBroker.isPresent()) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            log.info(" Broker id: {} content: {}", id, optionalBroker.get());
            return ResponseUtils.jsonOkResponse(optionalBroker.get());
        } catch (Exception ex) {
            log.error("Failed to fetch broker id with exception: " + id, ex);
            return Response.serverError().build();
        }
    }
}
