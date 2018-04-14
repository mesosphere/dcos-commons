package com.mesosphere.sdk.scheduler;

import java.util.Collection;
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
     * Called periodically to check the client status. These responses are generally used to tell upstream something
     * about the state of the client, and/or telling upstream to do something.
     */
    public ClientStatusResponse getClientStatus();

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
    public TaskStatusResponse taskStatus(Protos.TaskStatus status);

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
     * Response object to be returned by a call to {@link #getClientStatus()}.
     */
    public static class ClientStatusResponse {

        /**
         * The outcome value to be included in a response object.
         */
        public enum Result {
            /**
             * The client is deploying (or reconfiguring) the reservations for the underlying service. This hint allows
             * upstream to ensure that only N services are growing their footprint in the cluster at a time. This allows
             * multi-service schedulers to reduce the likelihood of deadlocks between two services attempting to deploy
             * into resources that are too small to fit both of them.
             */
            RESERVING,

            /**
             * The client has finished its reservation stage and is now in an active, running state.
             */
            RUNNING,

            /**
             * The client has finished running and should be switched to uninstall mode. This is used for services which
             * run once and then exit, and is only applicable to multi-service mode.
             */
            FINISHED,

            /**
             * The client has finished its uninstall. In multi-service mode, this means that the service can be removed
             * from the parent scheduler. When uninstalling the scheduler itself, this means that the scheduler can be
             * torn down, assuming that all other services have also reached {@code UNINSTALLED}.
             */
            UNINSTALLED
        };

        /**
         * The result of the call.
         */
        public final Result result;

        /**
         * Tells the caller that this client is deploying the service. This may either be an initial deployment or a
         * redeployment following a configuration change, either of which may involve growing the service footprint.
         * This does NOT include cases of relaunching failed tasks.
         *
         * This code has two purposes:
         * <ul><li>Allow upstream to detect a stalled deployment (cluster too small, or other misconfiguration)</li>
         * <li>Allow a multi-service scheduler to only have one service deploying at a time, to prevent deadlocks if the
         * cluster doesn't have enough free room for multiple simultaneous services</li></ul>
         */
        public static ClientStatusResponse reserving() {
            return new ClientStatusResponse(Result.RESERVING);
        }

        /**
         * Tells the caller that this client is running the service following a completed deployment. This is the
         * typical state for healthy services. It is also used for services that are in the process of uninstalling.
         */
        public static ClientStatusResponse running() {
            return new ClientStatusResponse(Result.RUNNING);
        }

        /**
         * Tells the caller that this client has finished running and can be switched to uninstall mode. The caller
         * should long-decline any unused offers.
         */
        public static ClientStatusResponse finished() {
            return new ClientStatusResponse(Result.FINISHED);
        }

        /**
         * Tells the caller that this client has finished uninstalling and can be shut down. After this is returned, the
         * caller may then notify the client of framework deregistration by calling {@link #unregistered()}, but this is
         * not required.
         */
        public static ClientStatusResponse uninstalled() {
            return new ClientStatusResponse(Result.UNINSTALLED);
        }

        private ClientStatusResponse(Result result) {
            this.result = result;
        }
    }

    /**
     * Response object to be returned by a call to {@link #offers(List)}.
     */
    public static class OfferResponse {

        /**
         * The outcome value to be included in a response object.
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
            PROCESSED
        }

        /**
         * The result of the call. Delineates between "not ready" (short-decline unused offers) and "processed"
         * (long-decline unused offers).
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

        private OfferResponse(Result result, Collection<OfferRecommendation> recommendations) {
            this.result = result;
            this.recommendations = recommendations;
        }
    }

    /**
     * Response object to be returned by a call to {@link #getUnexpectedResources(List)}.
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
     * Response object to be returned by a call to {@link #taskStatus(org.apache.mesos.Protos.TaskStatus)}.
     */
    public static class TaskStatusResponse {

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
        public static TaskStatusResponse unknownTask() {
            return new TaskStatusResponse(Result.UNKNOWN_TASK);
        }

        /**
         * Tells the caller that this client successfully processed the provided task status.
         */
        public static TaskStatusResponse processed() {
            return new TaskStatusResponse(Result.PROCESSED);
        }

        private TaskStatusResponse(Result result) {
            this.result = result;
        }
    }
}
