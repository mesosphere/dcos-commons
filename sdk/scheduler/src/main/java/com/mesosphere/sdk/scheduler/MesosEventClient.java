package com.mesosphere.sdk.scheduler;

import java.util.Collection;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
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
     * returned {@link OfferResponse#readyToUninstall()} as a response. When this is called, the service should
     * advertise the completion of the uninstall operation in its {@code deploy} plan.
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
             * The client is actively performing work, e.g. acquiring footprint or launching tasks. Additional details
             * about its operation may be obtained by checking the value of {@code workingStatus}. These details may
             * be used to determine e.g. whether the service currently needs any offers.
             */
            WORKING,

            /**
             * The client has no work pending and currently does not require any offers at all (hint: offers can be
             * suppressed if all services are idle). However, any relevant task statuses should still be sent, and the
             * response may contain an {@code idleRequest} for any actions to be taken upstream.
             */
            IDLE
        };

        /**
         * Provides additional information about a {@code WORKING} service, which may be used to determine which
         * services should be receiving offers at any given time.
         */
        public static class WorkingStatus {

            /**
             * This gives additional information about the status of a {@code WORKING} service. Upstream can use this
             * information to decide which clients should be receiving offers at any given time.
             */
            public enum State {
                /**
                 * The client is acquiring new footprint for the underlying service. This includes initial deployment,
                 * reconfiguration, and replacement of tasks. It does NOT include restarting tasks or launching sidecar
                 * tasks, as those operations are performed within the existing footprint.
                 *
                 * This hint allows upstream to ensure that only N services are growing their footprint in the cluster
                 * at a time. Multi-service schedulers can use this information to reduce the likelihood of deadlocks
                 * between two services attempting to deploy into the same resources at the same time.
                 */
                FOOTPRINT,

                /**
                 * The client is launching tasks for the underlying service into an existing footprint. This includes
                 * initial deployment after the footprint has been acquired, restarting failed tasks in-place, and
                 * launching sidecar tasks.
                 */
                LAUNCH
            }

            /**
             * @see State
             */
            public final State state;

            /**
             * Whether the service has a new or different workload since the previous status call. This is an indicator
             * that previously declined offers should be revived.
             *
             * In practice, this is always {@code false} when the {@link State} is {@code IDLE}.
             */
            public final boolean hasNewWork;

            private WorkingStatus(State state, boolean hasNewWork) {
                this.state = state;
                this.hasNewWork = hasNewWork;
            }

            @Override
            public boolean equals(Object o) {
                return EqualsBuilder.reflectionEquals(this, o);
            }

            @Override
            public int hashCode() {
                return HashCodeBuilder.reflectionHashCode(this);
            }

            @Override
            public String toString() {
                return (hasNewWork) ? String.format("%s+newWork", state) : state.toString();
            }
        }

        /**
         * This notifies upstream of some action that should be performed against this service.
         */
        public enum IdleRequest {

            /**
             * The client has no requested actions for upstream to perform.
             */
            NONE,

            /**
             * The client has finished running and should be switched to uninstall mode by the caller. This is used
             * for services which run once and then exit, and is only applicable to multi-service mode.
             */
            START_UNINSTALL,

            /**
             * The client has finished its uninstall. In multi-service mode, this means that the service can be
             * removed from the parent scheduler. When uninstalling the scheduler itself, this means that the
             * scheduler can be torn down, assuming that all other services have also reached {@code UNINSTALLED}.
             */
            REMOVE_CLIENT
        }

        /**
         * The status of the service, either {@code WORKING} or {@code IDLE}. Additional detail for either of these
         * states can be found via {@code workingStatus} or {@code idleRequest}, respectively.
         */
        public final Result result;

        /**
         * Additional detail for a {@code WORKING} service's status. This is only set if the {@code result} is
         * {@code WORKING}, otherwise it is {@code null}.
         */
        public final WorkingStatus workingStatus;

        /**
         * Additional action required for an {@code IDLE} service. This is set only if the {@code result} is
         * {@code IDLE}, otherwise it is {@code null}.
         */
        public final IdleRequest idleRequest;

        /**
         * Tells the caller that this client is deploying the service's footprint. This may either be an initial
         * deployment or a re-deployment following a configuration change, either of which may involve changing the
         * service footprint. This does NOT include in-place restarts of failed tasks, nor launches of sidecar tasks
         * which were already allocated.
         *
         * This code has two purposes:
         * <ul><li>Allow upstream to detect a stalled deployment (cluster too small, or other misconfiguration)</li>
         * <li>Allow a multi-service scheduler to only have one service deploying at a time, to prevent deadlocks if the
         * cluster doesn't have enough free room for multiple simultaneous services</li></ul>
         */
        public static ClientStatusResponse footprint(boolean hasNewWork) {
            return new ClientStatusResponse(
                    Result.WORKING, new WorkingStatus(WorkingStatus.State.FOOTPRINT, hasNewWork), null);
        }

        /**
         * Tells the caller that this client is launching tasks into existing footprint. This may either be initial
         * deployment of tasks into a previously acquired footprint, or restarting tasks into prior footprint, or
         * launching sidecar tasks into prior footprint.
         */
        public static ClientStatusResponse launching(boolean hasNewWork) {
            return new ClientStatusResponse(
                    Result.WORKING, new WorkingStatus(WorkingStatus.State.LAUNCH, hasNewWork), null);
        }

        /**
         * Tells the caller that this client has no work pending and does not need to receive offers right now. However
         * it should continue to receive any task statuses that are relevant to it.
         */
        public static ClientStatusResponse idle() {
            return new ClientStatusResponse(Result.IDLE, null, IdleRequest.NONE);
        }

        /**
         * Tells the caller that this client has finished running and can be switched to uninstall mode. The caller
         * should long-decline any unused offers.
         */
        public static ClientStatusResponse readyToUninstall() {
            return new ClientStatusResponse(Result.IDLE, null, IdleRequest.START_UNINSTALL);
        }

        /**
         * Tells the caller that this client has finished uninstalling and can be torn down. After this is returned, the
         * caller may then notify the client of framework deregistration by calling {@link #unregistered()}, but this is
         * not required.
         *
         * This status is only relevant to multi-service schedulers.
         */
        public static ClientStatusResponse readyToRemove() {
            return new ClientStatusResponse(Result.IDLE, null, IdleRequest.REMOVE_CLIENT);
        }

        private ClientStatusResponse(Result result, WorkingStatus workingStatus, IdleRequest idleRequest) {
            this.result = result;
            this.workingStatus = workingStatus;
            this.idleRequest = idleRequest;
        }

        @Override
        public boolean equals(Object o) {
            return EqualsBuilder.reflectionEquals(this, o);
        }

        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(result.toString());
            if (workingStatus != null) {
                sb.append('/');
                sb.append(workingStatus.toString());
            } else if (idleRequest != null) {
                sb.append('/');
                sb.append(idleRequest.toString());
            }
            return sb.toString();
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

        @Override
        public boolean equals(Object o) {
            return EqualsBuilder.reflectionEquals(this, o);
        }

        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
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

        @Override
        public boolean equals(Object o) {
            return EqualsBuilder.reflectionEquals(this, o);
        }

        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
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

        @Override
        public boolean equals(Object o) {
            return EqualsBuilder.reflectionEquals(this, o);
        }

        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this);
        }
    }
}
