package com.mesosphere.sdk.framework;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.mesos.Protos;
import org.apache.mesos.SchedulerDriver;
import org.slf4j.Logger;

import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.DestroyOfferRecommendation;
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.offer.OfferAccepter;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.OfferUtils;
import com.mesosphere.sdk.offer.UnreserveOfferRecommendation;
import com.mesosphere.sdk.scheduler.MesosEventClient;
import com.mesosphere.sdk.scheduler.Metrics;
import com.mesosphere.sdk.scheduler.OfferResources;
import com.mesosphere.sdk.scheduler.MesosEventClient.OfferResponse;
import com.mesosphere.sdk.scheduler.MesosEventClient.ClientStatusResponse;
import com.mesosphere.sdk.scheduler.MesosEventClient.UnexpectedResourcesResponse;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.PersisterUtils;

/**
 * Handles offer processing for the framework, passing offers to an underlying {@link MesosEventClient}, which itself
 * represents one or more underlying services.
 */
class OfferProcessor {

    private static final Logger LOGGER = LoggingUtils.getLogger(OfferProcessor.class);
    private static final Duration DEFAULT_OFFER_WAIT = Duration.ofSeconds(5);

    // Avoid attempting to process offers until initialization has completed via the first call to registered().
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    // After deregistration has occurred, drop offers without trying to decline them (driver not running anymore!)
    private final AtomicBoolean isDeregistered = new AtomicBoolean(false);

    // Executor for processing offers off the queue in {@link #start()}.
    private final ExecutorService offerExecutor = Executors.newSingleThreadExecutor();

    private final Object inProgressLock = new Object();
    private final Set<Protos.OfferID> offersInProgress = new HashSet<>();

    private final MesosEventClient mesosEventClient;
    private final Persister persister;
    private final OfferAccepter offerAccepter;

    // Internal TokenBucket may be overridden in tests:
    private ReviveManager reviveManager;
    // May be overridden in tests:
    private OfferQueue offerQueue;
    // Whether we should run in multithreaded mode. Should only be disabled for tests.
    private boolean multithreaded;

    public OfferProcessor(MesosEventClient mesosEventClient, Persister persister) {
        this.mesosEventClient = mesosEventClient;
        this.persister = persister;
        this.offerAccepter = new OfferAccepter();
        this.reviveManager = new ReviveManager(TokenBucket.newBuilder().build());
        this.offerQueue = new OfferQueue();
        this.multithreaded = true;
    }

    /**
     * Forces the instance to run in a synchronous/single-threaded mode for tests. To have any effect, this must be
     * called before calling {@link #start()}.
     *
     * @return {@code this}
     */
    @VisibleForTesting
    OfferProcessor disableThreading() {
        multithreaded = false;
        return this;
    }

    /**
     * Overrides the offer queue size. Must only be called before the scheduler has {@link #start()}ed.
     *
     * @param queueSize the queue size to use, zero for infinite
     * @return {@code this}
     */
    @VisibleForTesting
    OfferProcessor setOfferQueueSize(int queueSize) {
        offerQueue = new OfferQueue(queueSize);
        return this;
    }

    /**
     * Overrides the token bucket used to handle rate limiting of revive calls.
     *
     * @param reviveTokenBucket the replacement {@link TokenBucket}
     * @return {@code this}
     */
    @VisibleForTesting
    OfferProcessor setReviveTokenBucket(TokenBucket reviveTokenBucket) {
        this.reviveManager = new ReviveManager(reviveTokenBucket);
        return this;
    }

    public void start() {
        if (multithreaded) {
            // Start consumption of the offer queue. This will idle until offers start arriving.
            offerExecutor.execute(() -> {
                while (true) {
                    try {
                        processQueuedOffers(DEFAULT_OFFER_WAIT);
                    } catch (Exception e) {
                        LOGGER.error("Error encountered when processing offers, exiting to avoid zombie state", e);
                        ProcessExit.exit(ProcessExit.ERROR, e);
                    }
                }
            });
        }

        isInitialized.set(true);
    }

    public void enqueue(List<Protos.Offer> offers) {
        synchronized (inProgressLock) {
            offersInProgress.addAll(
                    offers.stream()
                            .map(offer -> offer.getId())
                            .collect(Collectors.toList()));

            LOGGER.info("Enqueuing {} offer{}. Updated offers in progress: {}",
                    offers.size(),
                    offers.size() == 1 ? "" : "s",
                    offersInProgress.stream()
                            .map(offerID -> offerID.getValue())
                            .collect(Collectors.toList()));
        }

        if (!offers.isEmpty()) {
            // We've gotten some offers, so we're not suppressed anymore.
            reviveManager.notifyOffersReceived();
        }

        for (Protos.Offer offer : offers) {
            boolean queued = offerQueue.offer(offer);
            if (!queued) {
                LOGGER.warn("Offer queue is full: Declining offer and removing from in progress: '{}'",
                        offer.getId().getValue());
                declineShort(Arrays.asList(offer));
                // Remove AFTER decline: Avoid race where we haven't declined yet but appear to be done
                synchronized (inProgressLock) {
                    offersInProgress.remove(offer.getId());
                }
            }
        }

        if (!multithreaded) {
            // Immediately process on this thread, rather than depending on offerExecutor to do it.
            // In the single-threaded case, we also disable waiting for offers to come in.
            processQueuedOffers(Duration.ZERO);
        }
    }

    public void dequeue(Protos.OfferID offerId) {
        offerQueue.remove(offerId);
    }

    /**
     * All offers must have been presented to resourceOffers() before calling this.  This call will block until all
     * offers have been processed.
     *
     * @throws InterruptedException if waiting for offers to be processed is interrupted
     * @throws IllegalStateException if offers were not processed in a reasonable amount of time
     */
    @VisibleForTesting
    public void awaitOffersProcessed() throws InterruptedException {
        final int totalDurationMs = 5000;
        final int sleepDurationMs = 100;
        for (int i = 0; i < totalDurationMs / sleepDurationMs; ++i) {
            synchronized (inProgressLock) {
                if (offersInProgress.isEmpty()) {
                    LOGGER.info("All offers processed.");
                    return;
                }
                LOGGER.warn("Offers in progress {} is non-empty, sleeping for {}ms ...",
                        offersInProgress.stream().map(id -> id.getValue()).collect(Collectors.toList()),
                        sleepDurationMs);
            }
            Thread.sleep(sleepDurationMs);
        }
        throw new IllegalStateException(String.format(
                "Timed out after %dms waiting for offers to be processed", totalDurationMs));
    }

    /**
     * Dequeues and processes any elements which are present on the offer queue, potentially blocking up to
     * {@code queueWait} for offers to appear.
     */
    private void processQueuedOffers(Duration queueWait) {
        LOGGER.info("Waiting up to {}s for offers...", queueWait.getSeconds());
        List<Protos.Offer> offers = offerQueue.takeAll(queueWait);
        try {
            // Always check if a revive is needed:
            reviveManager.reviveIfRequested();

            if (offers.isEmpty() && !isInitialized.get()) {
                // The scheduler hasn't finished registration yet, so many members haven't been initialized either.
                // Avoid hitting NPE for planCoordinator, driver, etc.
                LOGGER.info("Retrying wait for offers: Registration hasn't completed yet.");
                return;
            } else if (isDeregistered.get()) {
                // The scheduler has deregistered following a completed uninstall, but there may yet be unflushed offers
                // in the queue.
                if (!offers.isEmpty()) {
                    LOGGER.info("Dropping {} queued offers: Framework is deregistered", offers.size());
                }
                return;
            }

            // Match offers with work (call into implementation)
            final Timer.Context context = Metrics.getProcessOffersDurationTimer();
            try {
                if (checkStatus()) {
                    evaluateOffers(offers);
                }
            } finally {
                context.stop();
            }
        } finally {
            Metrics.incrementProcessedOffers(offers.size());

            synchronized (inProgressLock) {
                offersInProgress.removeAll(
                        offers.stream()
                                .map(offer -> offer.getId())
                                .collect(Collectors.toList()));
                if (!offers.isEmpty()) {
                    LOGGER.info("Processed {} queued offer{}. {} {} in progress: {}",
                            offers.size(),
                            offers.size() == 1 ? "" : "s",
                            offersInProgress.size(),
                            offersInProgress.size() == 1 ? "offer remains" : "offers remain",
                            offersInProgress.stream().map(Protos.OfferID::getValue).collect(Collectors.toList()));
                }
            }
        }
    }

    /**
     * Checks the status of the client and returns whether it should be provided with offers.
     */
    private boolean checkStatus() {
        ClientStatusResponse response = mesosEventClient.getClientStatus();
        LOGGER.info("Status result: {}", response.result);

        switch (response.result) {
        case RUNNING:
            if (response.runningStatus.hasNewWork) {
                // Service has new work. Revive any previously declined offers.
                reviveManager.requestRevive();
            }

            final boolean offersNeeded;
            if (response.runningStatus.state == ClientStatusResponse.RunningStatus.State.IDLE) {
                // Service is idle (multi-service: all idle). Suppress offers.
                offersNeeded = false;
            } else {
                // Service is not idle (multi-service: any not idle). Revive offers if suppressed.
                // Note: We don't care about FOOTPRINT vs LAUNCH at this level. That's instead being used in
                // MultiServiceEventClient to implement offer discipline (e.g. avoid too many services getting FOOTPRINT
                // at the same time)
                offersNeeded = true;
            }
            reviveManager.notifyOffersNeeded(offersNeeded);
            return offersNeeded;
        case READY_TO_UNINSTALL:
            // We do not directly support this result at this level. It should only occur in multi-service deployments,
            // where it would have been handled upstream in the MultiServiceEventClient.
            LOGGER.error("Got unsupported {} from service", response.result);
            throw new IllegalStateException(String.format(
                    "Got unsupported %s response. This should have been handled by a MultiServiceEventClient",
                    response.result));
        case READY_TO_REMOVE:
            // The managed service(s) have finished uninstalling and there's nothing left to do.
            // Unregister and delete the framework.
            isDeregistered.set(true);
            destroyFramework();
            return false;
        }

        throw new IllegalStateException("Unsupported ClientStatusResponse result: " + response.result);
    }

    private void evaluateOffers(List<Protos.Offer> offers) {
        // Offer evaluation:
        // The client (which is composed of one or more services) looks at the provided offers and returns a list of
        // operations to perform and offers which were not used. On our end, we then perform the requested operations
        // and clean or decline the remaining unused offers.
        OfferResponse offerResponse = mesosEventClient.offers(offers);
        if (!offers.isEmpty() || !offerResponse.recommendations.isEmpty()) {
            LOGGER.info("Offer result for {} offer{}: {} with {} recommendation{}",
                    offers.size(), offers.size() == 1 ? "" : "s",
                    offerResponse.result,
                    offerResponse.recommendations.size(), offerResponse.recommendations.size() == 1 ? "" : "s");
        }

        Collection<Protos.Offer> unusedOffers =
                OfferUtils.filterOutAcceptedOffers(offers, offerResponse.recommendations);

        // Resource Cleaning is needed to clean up offered resources in several scenarios:
        // - A service may be uninstalling, in which case all of their resources will appear to be 'unexpected'.
        // - A service may want to decommission a subset of its tasks, in which case they will appear as 'unexpected',
        //   even though the overall service is not being uninstalled.
        // - Mesos Agents may become inoperable for long enough that Tasks resident there were relocated and the old
        //   resources were effectively forgotten by the service. However, this Agent may return at a later point and
        //   begin offering those forgotten reserved Resources again.
        // In all of these cases, we unreserve these unexpected resources so that they may be returned to the cluster.
        // To do this we perform all necessary UNRESERVE and/or DESTROY Operations against those resources.
        // Note: To keep things simple, we only perform this cleanup against offers which were not used in earlier
        // steps, to e.g. launch other tasks. Unused reserved resources within these offers will be cleaned when they
        // are offered again in a following offer cycle, assuming we don't use them again for something else.

        UnexpectedResourcesResponse.Result cleanupResult = UnexpectedResourcesResponse.Result.PROCESSED;
        Collection<OfferRecommendation> cleanupRecommendations = Collections.emptyList();
        if (!unusedOffers.isEmpty()) {
            UnexpectedResourcesResponse unexpectedResourcesResponse =
                    mesosEventClient.getUnexpectedResources(unusedOffers);
            cleanupResult = unexpectedResourcesResponse.result;
            cleanupRecommendations = toCleanupRecommendations(unexpectedResourcesResponse.offerResources);
            LOGGER.info("Cleanup result for {} offer{}: {} with {} recommendation{}",
                    unusedOffers.size(), unusedOffers.size() == 1 ? "" : "s",
                    unexpectedResourcesResponse.result,
                    cleanupRecommendations.size(), cleanupRecommendations.size() == 1 ? "" : "s");
        }

        // Decline the offers that haven't been used for either offer evaluation or resource cleanup.
        unusedOffers = OfferUtils.filterOutAcceptedOffers(unusedOffers, cleanupRecommendations);
        if (!unusedOffers.isEmpty()) {
            if (offerResponse.result == OfferResponse.Result.PROCESSED
                    && cleanupResult == UnexpectedResourcesResponse.Result.PROCESSED) {
                // The client successfully processed offers and unexpected resources.
                // Decline the unused offers for a long interval.
                declineLong(unusedOffers);
            } else {
                // The client wasn't ready to process offers and/or failed to process unexpected resources.
                // Decline the unused offers for a brief interval.
                declineShort(unusedOffers);
            }
        }

        // Accept the offers which have operations to be performed against them:
        List<OfferRecommendation> allRecommendations = new ArrayList<>();
        allRecommendations.addAll(offerResponse.recommendations);
        allRecommendations.addAll(cleanupRecommendations);
        Metrics.incrementRecommendations(allRecommendations);
        offerAccepter.accept(allRecommendations);
    }

    /**
     * Destroys the framework.
     */
    private void destroyFramework() {
        // Wipe all data from ZK. This includes the framework ID, which is used to detect across restarts that the
        // framework has been destroyed.
        LOGGER.info("Deleting all persisted data...");
        try {
            PersisterUtils.clearAllData(persister);
        } catch (PersisterException e) {
            throw new IllegalStateException("Failed to delete all persister data", e);
        }

        LOGGER.info("Tearing down framework...");
        Optional<SchedulerDriver> driver = Driver.getDriver();
        if (driver.isPresent()) {
            // Stop the SchedulerDriver thread:
            // - failover==false: Tells Mesos to teardown the framework.
            // - This call will cause FrameworkRunner's SchedulerDriver.run() call to return DRIVER_STOPPED.
            driver.get().stop(false);
        } else {
            LOGGER.error("No driver is present for deregistering the framework.");
        }

        LOGGER.info("### UNINSTALL IS COMPLETE! ###");
        LOGGER.info("Scheduler should be cleaned up shortly...");

        // Notify the client that deregistration has completed, following them giving us an UNINSTALLED response.
        // They can then set their "deploy" plan to Complete, which will in turn let Cosmos know that this scheduler
        // process can be pruned from Marathon.
        mesosEventClient.unregistered();
    }

    /**
     * Converts the provided {@code OfferResources} instances into an ordered list of destroy and/or unreserve
     * operations.
     */
    private static Collection<OfferRecommendation> toCleanupRecommendations(
            Collection<OfferResources> offerResourcesList) {
        // ORDERING IS IMPORTANT:
        //    The resource lifecycle is RESERVE -> CREATE -> DESTROY -> UNRESERVE
        //    Therefore we *must* put any DESTROY calls before any UNRESERVE calls
        List<OfferRecommendation> destroyRecommendations = new ArrayList<>();
        List<OfferRecommendation> unreserveRecommendations = new ArrayList<>();

        for (OfferResources offerResources : offerResourcesList) {
            for (Protos.Resource resource : offerResources.getResources()) {
                if (resource.hasDisk() && resource.getDisk().hasPersistence()) {
                    // Permanent volume to be DESTROYed (and also UNRESERVEd)
                    destroyRecommendations.add(new DestroyOfferRecommendation(offerResources.getOffer(), resource));
                }
                // Reserved resource OR permanent volume to be UNRESERVEd
                unreserveRecommendations.add(new UnreserveOfferRecommendation(offerResources.getOffer(), resource));
            }
        }

        // Order the recommendations as DESTROYs followed by UNRESERVEs, as mentioned above:
        List<OfferRecommendation> allRecommendations = new ArrayList<>();
        allRecommendations.addAll(destroyRecommendations);
        allRecommendations.addAll(unreserveRecommendations);
        return allRecommendations;
    }

    /**
     * Declines the provided offers for a short time. This is used for special cases when the scheduler hasn't fully
     * initialized and otherwise wasn't able to actually look at the offers in question. At least not yet.
     */
    static void declineShort(Collection<Protos.Offer> unusedOffers) {
        declineOffers(unusedOffers, Constants.SHORT_DECLINE_SECONDS);
        Metrics.incrementDeclinesShort(unusedOffers.size());
    }

    /**
     * Declines the provided offers for a long time. This is used for offers which were not useful to the scheduler.
     */
    private static void declineLong(Collection<Protos.Offer> unusedOffers) {
        declineOffers(unusedOffers, Constants.LONG_DECLINE_SECONDS);
        Metrics.incrementDeclinesLong(unusedOffers.size());
    }

    /**
     * Decline unused {@link org.apache.mesos.Protos.Offer}s.
     *
     * @param unusedOffers The collection of Offers to decline
     * @param refuseSeconds The number of seconds for which the offers should be refused
     */
    private static void declineOffers(Collection<Protos.Offer> unusedOffers, int refuseSeconds) {
        Optional<SchedulerDriver> driver = Driver.getDriver();
        if (!driver.isPresent()) {
            throw new IllegalStateException("No driver present for declining offers.  This should never happen.");
        }

        Collection<Protos.OfferID> offerIds = unusedOffers.stream()
                .map(offer -> offer.getId())
                .collect(Collectors.toList());
        LOGGER.info("Declining {} unused offer{} for {} seconds: {}",
                offerIds.size(),
                offerIds.size() == 1 ? "" : "s",
                refuseSeconds,
                offerIds.stream().map(Protos.OfferID::getValue).collect(Collectors.toList()));
        final Protos.Filters filters = Protos.Filters.newBuilder()
                .setRefuseSeconds(refuseSeconds)
                .build();
        offerIds.forEach(offerId -> driver.get().declineOffer(offerId, filters));
    }
}
