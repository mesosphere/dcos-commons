package com.mesosphere.sdk.scheduler;

import com.google.common.annotations.VisibleForTesting;
import org.apache.mesos.Protos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * This class acts as a buffer of Offers from Mesos.  By default it holds a maximum of 100 Offers.
 */
public class OfferQueue {
    private static final int DEFAULT_CAPACITY = 100;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final BlockingQueue<Protos.Offer> queue;

    public OfferQueue() {
        this(DEFAULT_CAPACITY);
    }

    public OfferQueue(int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    /**
     * Calling this method is a blocking Operation. It returns all Offers currently in the queue. It always returns at
     * least one Offer.
     */
    public List<Protos.Offer> takeAll() {
        List<Protos.Offer> offers = new LinkedList<>();
        try {
            // The take() call is blocking, so waits for at least one Offer is returned.  The following drainTo() call
            // will pull the remainder of Offers (including zero Offers) off the queue and return.
            offers.add(queue.take());
            queue.drainTo(offers);
        } catch (InterruptedException e) {
            logger.warn("Interrupted while waiting for offer in queue.");
        }

        return offers;
    }

    /**
     * This method enqueues an Offer from Mesos if there is capacity. If there is not capacity the Offer is not added
     * to the queue.
     * @return true if the Offer was successfully put in the queue, false otherwise
     */
    public boolean offer(Protos.Offer offer) {
        return queue.offer(offer);
    }

    /**
     * This method removes an offer from the queue based on its OfferID.
     */
    public void remove(Protos.OfferID offerID) {
        Collection<Protos.Offer> offers = queue.parallelStream()
                .filter(offer -> offer.getId().equals(offerID))
                .collect(Collectors.toList());

        boolean removed = queue.removeAll(offers);
        if (!removed) {
            logger.warn("Attempted to remove offer: '{}' but it was not present in the queue.", offerID.getValue());
        } else {
            logger.info("Removed offer: {}", offerID.getValue());
        }
    }

    /**
     * This method specifies whether any offers are in the queue.
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * This method returns the number of elements in the queue.
     */
    @VisibleForTesting
    int getSize() {
        return queue.size();
    }

    /**
     * This method returns the remaining capacity in the queue.
     */
    @VisibleForTesting
    int getRemainingCapacity() {
        return queue.remainingCapacity();
    }
}
