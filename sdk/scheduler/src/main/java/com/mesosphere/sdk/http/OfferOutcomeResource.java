package com.mesosphere.sdk.http;

import com.mesosphere.sdk.offer.history.OfferOutcomeTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

/**
 *  A read-only API for accessing the most recently processed offers. It does _not_ return any information
 *  about offers that were declined but never evaluated.
 */
@Path("/v1/debug/offers")
public class OfferOutcomeResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(OfferOutcomeResource.class);

    private final OfferOutcomeTracker offerOutcomeTracker;

    public OfferOutcomeResource(OfferOutcomeTracker offerOutcomeTracker) {
        this.offerOutcomeTracker = offerOutcomeTracker;
    }

    /**
     * Renders the current set of offer outcomes as an HTML table.
     * @return HTML response of the table.
     */
    @GET
    public Response getOfferOutcomes(@QueryParam("json") boolean json) {
        if (json) {
            return ResponseUtils.jsonOkResponse(offerOutcomeTracker.toJson());
        } else {
            return ResponseUtils.htmlOkResponse(offerOutcomeTracker.toHtml());
        }
    }
}
