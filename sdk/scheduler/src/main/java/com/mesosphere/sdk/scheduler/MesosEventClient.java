package com.mesosphere.sdk.scheduler;

import java.util.Collection;
import java.util.List;

import org.apache.mesos.Protos;

/**
 * Accepts events received from Mesos.
 */
public interface MesosEventClient {

    /**
     * Response object to be returned by {@code offers()}.
     */
    public static class OfferResponse {

        /**
         * The outcome of this offers call.
         */
        public enum Result {
            /**
             * The client was not ready to process these offers. Come back later.
             */
            NOT_READY,

            /**
             * The client processed these offers and was not interested in them.
             */
            PROCESSED
        }

        /**
         * The result of the call. Delineates between "not ready" and "processed".
         */
        public final Result result;

        /**
         * The offers which were not used to deploy something.
         */
        public final List<Protos.Offer> unusedOffers;

        public static OfferResponse notReady(List<Protos.Offer> allOffers) {
            return new OfferResponse(Result.NOT_READY, allOffers);
        }

        public static OfferResponse processed(List<Protos.Offer> unusedOffers) {
            return new OfferResponse(Result.PROCESSED, unusedOffers);
        }

        private OfferResponse(Result result, List<Protos.Offer> unusedOffers) {
            this.result = result;
            this.unusedOffers = unusedOffers;
        }
    }

    /**
     * Response object to be returned by {@code status()}.
     */
    public static class StatusResponse {

        /**
         * The outcome of this status call.
         */
        public enum Result {
            /**
             * The task is not known to this service.
             */
            UNKNOWN_TASK,

            /**
             * The task status was processed.
             */
            PROCESSED
        }

        /**
         * The result of the call. Delineates between "unknown" and "processed".
         */
        public final Result result;

        public static StatusResponse unknownTask() {
            return new StatusResponse(Result.UNKNOWN_TASK);
        }

        public static StatusResponse processed() {
            return new StatusResponse(Result.PROCESSED);
        }

        private StatusResponse(Result result) {
            this.result = result;
        }
    }

    /**
     * Type which accepts HTTP resource objects from a client to be served.
     */
    public interface ResourceServer {
        /**
         * Assigns the provided resources to this server. Resources will be served under the specified namespace.
         */
        void addResources(String namespace, Collection<Object> resources);
    }

    /**
     * Called when the framework has registered (or re-registered) with Mesos.
     */
    public void register(boolean reRegistered);

    /**
     * Called when the framework has received offers from Mesos. The provided list may be empty.
     *
     * @return The list of offers which were NOT used by this client. For example, a no-op call would just return the
     *         list of {@code offers} that were provided.
     */
    public OfferResponse offers(List<Protos.Offer> offers);

    /**
     * Called when the framework has received a task status update from Mesos.
     */
    public StatusResponse status(Protos.TaskStatus status);

    /**
     * Adds any HTTP resources to the provided {@link ResourceServer}. The implementation may add resources to the
     * server and return and/or keep the server around to periodically update its resources.
     */
    public void setResourceServer(ResourceServer resourceServer);
}
