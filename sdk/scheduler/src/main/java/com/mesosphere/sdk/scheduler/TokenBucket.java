package com.mesosphere.sdk.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a token bucket to limit the rate at which actions may be taken.  The assumption is that clients
 * will not take actions unless they receive a token via the {@link #tryAcquire()} method.
 */
public class TokenBucket {
    public static final int DEFAULT_CAPACITY = 256;
    public static final int DEFAULT_INITIAL_COUNT = DEFAULT_CAPACITY;
    public static final Duration DEFAULT_MIN_ACQUIRE_INTEVAL = Duration.ofSeconds(5);
    public static final Duration DEFAULT_MIN_INCREMENT_INTERVAL = Duration.ofSeconds(DEFAULT_CAPACITY);

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final int capacity;
    private final Duration incrementInterval;
    private final Duration acquireInterval;
    private int count;
    private long lastRevive = 0;

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * A TokenBucket acts as a rate limiting helper.  Clients should not perform rate limited work without acquiring a
     * token from the {@link #tryAcquire()} method.
     * @param initial The initial number of tokens available
     * @param capacity The maximum number of tokens the bucket may hold
     * @param incrementInterval The incrementInterval between adding a new token to the bucket
     */
    private TokenBucket(int initial, int capacity, Duration incrementInterval, Duration minAcquireInterval) {
        this.count = initial;
        this.capacity = capacity;
        this.incrementInterval = incrementInterval;
        this.acquireInterval = minAcquireInterval;

        String msg = String.format(
                "TokenBucket count: %d, capacity: %d, incrementInterval: %s, acquireInterval: %s",
                count, capacity, incrementInterval, acquireInterval);

        logger.info(msg);

        if (count < 0
                || capacity < 1
                || incrementInterval.isNegative()
                || incrementInterval.isZero()
                || minAcquireInterval.isNegative()) {
            throw new IllegalStateException(
                    String.format("TokenBucket construction failed with invalid configuration: %s", msg));
        }

        executor.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        increment();
                    }
                },
                incrementInterval.toMillis(),
                incrementInterval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * This method returns true if a rate-limited action should be executed, and false if the action should not occur.
     */
    public synchronized boolean tryAcquire() {
        if (count > 0 && durationHasPassed(acquireInterval)) {
            count--;
            lastRevive = System.currentTimeMillis();
            return true;
        }

        return false;
    }

    /**
     * This method adds a token to the bucket.
     */
    private synchronized void increment() {
        if (count < capacity) {
            count++;
        }
    }

    /**
     * This method is used to enforce a minimum incrementInterval between granting tokens.  It returns true when that
     * minimum duration has passed and false otherwise.
     */
    private boolean durationHasPassed(Duration duration) {
        return System.currentTimeMillis() - lastRevive >= duration.toMillis();
    }

    /**
     * This class is a builder for {@link TokenBucket}s.
     */
    public static final class Builder {
        private int initial = DEFAULT_INITIAL_COUNT;
        private int capacity = DEFAULT_CAPACITY;
        private Duration incrementInterval = DEFAULT_MIN_INCREMENT_INTERVAL;
        private Duration acquireInterval = DEFAULT_MIN_ACQUIRE_INTEVAL;

        /**
         * Set the initial number of tokens.
         */
        public Builder initialTokenCount(int count) {
            initial = count;
            return this;
        }

        /**
         * Set the maximum number of tokens.
         */
        public Builder capacity(int capacity) {
            this.capacity = capacity;
            return this;
        }

        /**
         * Set the interval between putting new tokens in the bucket.
         */
        public Builder incrementInterval(Duration incrementInterval) {
            this.incrementInterval = incrementInterval;
            return this;
        }

        /**
         * Set the minimum interval between acquiring tokens.
         */
        public Builder acquireInterval(Duration acquireInterval) {
            this.acquireInterval = acquireInterval;
            return this;
        }

        public TokenBucket build() {
            return new TokenBucket(initial, capacity, incrementInterval, acquireInterval);
        }
    }
}
