package com.mesosphere.sdk.api;

import com.mesosphere.sdk.offer.history.OfferOutcomeTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Path("/v1/debug/offers")
public class OfferOutcomeResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(OfferOutcomeResource.class);

    private final OfferOutcomeTracker offerOutcomeTracker;

    public OfferOutcomeResource(OfferOutcomeTracker offerOutcomeTracker) {
        this.offerOutcomeTracker = offerOutcomeTracker;
    }

    /**
     * Renders the current set of offer outcomes as an HTML <table>.
     * @return HTML response of the table.
     */
    @GET
    public Response getOfferOutcomes() {
        return ResponseUtils.htmlOkResponse(offerOutcomeTracker.toHtml());
    }
}
