package com.mesosphere.sdk.scheduler;

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
 * Created by gabriel on 7/13/17.
 */
public class OfferQueue {
    public static final int DEFAULT_CAPACITY = 100;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final BlockingQueue<Protos.Offer> queue;

    public OfferQueue () {
        this(DEFAULT_CAPACITY);
    }

    public OfferQueue(int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    public List<Protos.Offer> takeAll() {
        List<Protos.Offer> offers = new LinkedList<>();
        try {
            offers.add(queue.take());
            queue.drainTo(offers);
        } catch (InterruptedException e) {
            logger.warn("Interrupted while waiting for offer in queue.");
        }

        return offers;
    }

    public boolean offer(Protos.Offer offer) {
        return queue.offer(offer);
    }

    public void remove(Protos.OfferID offerID) {
        Collection<Protos.Offer> offers = queue.parallelStream()
                .filter(offer -> offer.getId().equals(offerID))
                .collect(Collectors.toList());

        boolean removed = queue.removeAll(offers);
        if (!removed) {
            logger.warn(
                    String.format(
                            "Attempted to remove offer: '%s' but it was not present in the queue.",
                            offerID.getValue()));
        } else {
            logger.info("Removed offer: {}", offerID.getValue());
        }
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
