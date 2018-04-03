package com.mesosphere.sdk.scheduler;

import java.util.Collection;
import java.util.Collections;
import org.apache.mesos.Protos;

import com.mesosphere.sdk.offer.OfferRecommendation;

/**
 * Accepts events received from Mesos.
 */
public interface MesosEventClient {

    /**
     * Called when the framework has registered (or re-registered) with Mesos.
     *
     * @param reRegistered Whether this is an initial registration ({@code false}) or a re-registration ({@code true})
     */
    public void registered(boolean reRegistered);

    /**
     * Called after the framework has been unregistered from Mesos following a call to {@link #offers(Collection)} which
     * returned {@link OfferResponse#finished()} as a response. When this is called, the service should advertise the
     * completion of the uninstall operation in its {@code deploy} plan.
     */
    public void unregistered();

    /**
     * Called when the framework has received offers from Mesos. The provided list may be empty.
     *
     * @param offers A list of offers which may be used in offer evaluation
     * @return The response containing a list of operations to be performed against the offers, as well as a list of
     *         offers which were left unused. See {@link OfferResponse}
     */
    public OfferResponse offers(Collection<Protos.Offer> offers);

    /**
     * Returns a list of resources from the provided list of offers which are not recognized by this client. The
     * returned unexpected resources will immediately be unreserved/destroyed.
     *
     * @param unusedOffers The list of offers which were unclaimed in a prior call to {@link #offers(Collection)}
     * @return A subset of the provided offers paired with their resources to be unreserved/destroyed. May be paired
     *         with an error state if remaining unused offers should be declined-short rather than declined-long.
     */
    public UnexpectedResourcesResponse getUnexpectedResources(Collection<Protos.Offer> unusedOffers);

    /**
     * Called when the framework has received a task status update from Mesos.
     *
     * @param status The status message describing the new state of a task
     * @return The response which describes whether the status was successfully processed
     */
    public StatusResponse status(Protos.TaskStatus status);

    /**
     * Returns any HTTP resources to be served on behalf of this instance.
     *
     * @return A list of annotated resource objects to be served by Jetty
     */
    public Collection<Object> getHTTPEndpoints();


    //////
    // RESPONSE TYPES
    //////


    /**
     * Response object to be returned by a call to {@link MesosEventClient#offers(List)}.
     */
    public static class OfferResponse {

        /**
         * The outcome value to be included in a response object.
         *
         * TODO(nickbp): Create a separate getStatus() call for retrieving special states like FINISHED/UNINSTALLED.
         * Putting them in the offer cycle like this is a bit of a hack. It's likely that we'll need other states and
         * putting them here would quickly get unsustainable. For example: Upfront footprint reservation in one phase,
         * followed by deployment in a second phase, where we want to notify upstream that we've finished the footprint.
         */
        public enum Result {
            /**
             * The client was not fully ready to process the request, but may have still processed some offers.
             * Short-decline any unused offers.
             */
            NOT_READY,

            /**
             * The client processed the request successfully. Long-decline any unused offers.
             */
            PROCESSED,

            /**
             * The client has finished running and should be switched to uninstall mode.
             */
            FINISHED,

            /**
             * The client has finished an uninstall and can be shut down.
             */
            UNINSTALLED
        }

        /**
         * The result of the call. Delineates between "not ready" (short-decline unused offers), "processed"
         * (long-decline unused offers), and "finished" (uninstall complete, tear down service).
         */
        public final Result result;

        /**
         * The operations to be performed using the provided offers.
         */
        public final Collection<OfferRecommendation> recommendations;

        /**
         * Tells the caller that this client was not completely ready to process offers. The caller should short-decline
         * any unused offers. Recommendations may still be returned, in which they should be performed in addition to
         * the short-decline of the remaining non-consumed offers.
         *
         * @param recommendations Operations to perform against some of the provided offers, which should be performed
         *                        despite the client returning a not ready status
         */
        public static OfferResponse notReady(Collection<OfferRecommendation> recommendations) {
            return new OfferResponse(Result.NOT_READY, recommendations);
        }

        /**
         * Tells the caller that this client was able to process offers. The caller should long-decline any unused
         * offers.
         *
         * @param recommendations Operations to perform against some of the provided offers
         */
        public static OfferResponse processed(Collection<OfferRecommendation> recommendations) {
            return new OfferResponse(Result.PROCESSED, recommendations);
        }

        /**
         * Tells the caller that this client has finished running and can be switched to uninstall mode. The caller
         * should long-decline any unused offers.
         */
        public static OfferResponse finished() {
            return new OfferResponse(Result.FINISHED, Collections.emptyList());
        }

        /**
         * Tells the caller that this client has finished uninstalling and can be shut down. The caller should
         * long-decline any unused offers. After this is returned, the caller may then notify the client of framework
         * deregistration by calling {@link MesosEventClient#unregistered()}, but this is not required.
         */
        public static OfferResponse uninstalled() {
            return new OfferResponse(Result.UNINSTALLED, Collections.emptyList());
        }

        private OfferResponse(Result result, Collection<OfferRecommendation> recommendations) {
            this.result = result;
            this.recommendations = recommendations;
        }
    }
    /**
     * Response object to be returned by a call to {@link MesosEventClient##getUnexpectedResources(List)}.
     */
    public static class UnexpectedResourcesResponse {

        /**
         * The outcome value to be included in a response object.
         */
        public enum Result {
            /**
             * The client failed to log unexpected resources. Don't unreserve them yet, come back later.
             */
            FAILED,

            /**
             * The client processed the request successfully.
             */
            PROCESSED
        }

        /**
         * The result of the call. Delineates between "failed" (decline short) and "processed" (unreserve/destroy).
         */
        public final Result result;

        /**
         * The resources which are unexpected, paired with their parent offers.
         */
        public final Collection<OfferResources> offerResources;

        /**
         * Tells the caller that this client failed to process unexpected offers, for example a failure to update a
         * write-ahead log with the resources that are about to be unreserved/destroyed. However, a subset of resources
         * may still be eligible for being unreserved/destroyed.
         *
         * @param unexpectedResources Any unexpected resources which should be unreserved and/or destroyed despite the
         *                            failure, or an empty collection if no dereservations should occur
         */
        public static UnexpectedResourcesResponse failed(Collection<OfferResources> unexpectedResources) {
            return new UnexpectedResourcesResponse(Result.FAILED, unexpectedResources);
        }

        /**
         * Tells the caller that this client was able to process unexpected resources. The caller should unreserve
         * and/or destroy the returned resources that were originally provided via the offers in the invocation.
         *
         * @param unexpectedResources Any unexpected resources which should be unreserved and/or destroyed
         */
        public static UnexpectedResourcesResponse processed(Collection<OfferResources> unexpectedResources) {
            return new UnexpectedResourcesResponse(Result.PROCESSED, unexpectedResources);
        }

        private UnexpectedResourcesResponse(Result result, Collection<OfferResources> offerResources) {
            this.result = result;
            this.offerResources = offerResources;
        }
    }

    /**
     * Response object to be returned by a call to {@link MesosEventClient#status(org.apache.mesos.Protos.TaskStatus)}.
     */
    public static class StatusResponse {

        /**
         * The outcome value to be included in a response object.
         */
        public enum Result {
            /**
             * The status is for a task which is unknown to the client.
             */
            UNKNOWN_TASK,

            /**
             * The client processed the request successfully.
             */
            PROCESSED
        }

        /**
         * The result of the call. Delineates between "unknown" and "processed".
         */
        public final Result result;

        /**
         * Tells the caller that this client did not recognize task described by this status. The caller should kill the
         * task so that its reserved resources will be offered by Mesos, at which point those resources will be
         * unreserved via the unexpected resources cleanup process.
         */
        public static StatusResponse unknownTask() {
            return new StatusResponse(Result.UNKNOWN_TASK);
        }

        /**
         * Tells the caller that this client successfully processed the provided task status.
         */
        public static StatusResponse processed() {
            return new StatusResponse(Result.PROCESSED);
        }

        private StatusResponse(Result result) {
            this.result = result;
        }
    }
}
