package com.mesosphere.sdk.framework;

import java.util.Arrays;
import java.util.Collection;
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
import com.mesosphere.sdk.offer.LoggingUtils;
import com.mesosphere.sdk.scheduler.AbstractScheduler;
import com.mesosphere.sdk.scheduler.Metrics;

/**
 * Handles offer processing for the framework, passing offers to an underlying {@link MesosEventClient}, which itself
 * represents one or more underlying services.
 */
public class OfferProcessor {

    private static final Logger LOGGER = LoggingUtils.getLogger(OfferProcessor.class);

    // Avoid attempting to process offers until initialization has completed via the first call to registered().
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    // After deregistration has occurred, drop offers without trying to decline them (driver not running anymore!)
    private final AtomicBoolean isDeregistered = new AtomicBoolean(false);

    // Executor for processing offers off the queue in {@link #start()}.
    private final ExecutorService offerExecutor = Executors.newSingleThreadExecutor();

    private final Object inProgressLock = new Object();
    private final Set<Protos.OfferID> offersInProgress = new HashSet<>();

    private final AbstractScheduler abstractScheduler;

    // May be overridden in tests:
    private OfferQueue offerQueue;
    // Whether we should run in multithreaded mode. Should only be disabled for tests.
    private boolean multithreaded;

    public OfferProcessor(AbstractScheduler abstractScheduler) {
        this.abstractScheduler = abstractScheduler;
        this.offerQueue = new OfferQueue();
        this.multithreaded = true;
    }

    /**
     * Forces the instance to run in a synchronous/single-threaded mode for tests. To have any effect, this must be
     * called before calling {@link #start()}.
     *
     * @return this
     */
    OfferProcessor disableThreading() {
        multithreaded = false;
        return this;
    }

    /**
     * Overrides the offer queue size. Must only be called before the scheduler has {@link #start()}ed.
     *
     * @param queueSize the queue size to use, zero for infinite
     * @return this
     */
    @VisibleForTesting
    OfferProcessor setOfferQueueSize(int queueSize) {
        offerQueue = new OfferQueue(queueSize);
        return this;
    }

    public void start() {
        if (multithreaded) {
            // Start consumption of the offer queue. This will idle until offers start arriving.
            offerExecutor.execute(() -> {
                while (true) {
                    try {
                        processQueuedOffers();
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
            // Process on this thread, rather than depending on offerExecutor to do it.
            processQueuedOffers();
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
     * Dequeues and processes any elements which are present on the offer queue, potentially blocking for offers to
     * appear.
     */
    private void processQueuedOffers() {
        LOGGER.info("Waiting for queued offers...");
        List<Protos.Offer> offers = offerQueue.takeAll();
        try {
            if (offers.isEmpty() && !isInitialized.get()) {
                // The scheduler hasn't finished registration yet, so many members haven't been initialized either.
                // Avoid hitting NPE for planCoordinator, driver, etc.
                LOGGER.info("Retrying wait for offers: Registration hasn't completed yet.");
                return;
            }

            // Match offers with work (call into implementation)
            final Timer.Context context = Metrics.getProcessOffersDurationTimer();
            try {
                evaluateOffers(offers);
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
                LOGGER.info("Processed {} queued offer{}. {} {} in progress: {}",
                        offers.size(),
                        offers.size() == 1 ? "" : "s",
                        offersInProgress.size(),
                        offersInProgress.size() == 1 ? "offer remains" : "offers remain",
                        offersInProgress.stream().map(Protos.OfferID::getValue).collect(Collectors.toList()));
            }
        }
    }

    private void evaluateOffers(List<Protos.Offer> offers) {
        if (isDeregistered.get()) {
            // Just in case there are unflushed offers in the queue when we're deregistered
            LOGGER.info("Dropping {} queued offers: Framework is deregistered", offers.size());
            return;
        }

        abstractScheduler.offers(offers);
    }

    /**
     * Declines the provided offers for a short time. This is used for special cases when the scheduler hasn't fully
     * initialized and otherwise wasn't able to actually look at the offers in question. At least not yet.
     */
    public static void declineShort(Collection<Protos.Offer> unusedOffers) {
        declineOffers(unusedOffers, Constants.SHORT_DECLINE_SECONDS);
        Metrics.incrementDeclinesShort(unusedOffers.size());
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
