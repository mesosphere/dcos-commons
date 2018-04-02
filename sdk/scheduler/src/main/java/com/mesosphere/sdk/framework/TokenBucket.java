package com.mesosphere.sdk.framework;

import org.slf4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.mesosphere.sdk.offer.LoggingUtils;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a token bucket to limit the rate at which actions may be taken.  The assumption is that clients
 * will not take actions unless they receive a token via the {@link #tryAcquire()} method.
 */
public class TokenBucket {
    private static final int DEFAULT_CAPACITY = 256;
    private static final int DEFAULT_INITIAL_COUNT = DEFAULT_CAPACITY;
    private static final Duration DEFAULT_ACQUIRE_INTERVAL = Duration.ofSeconds(5);
    private static final Duration DEFAULT_INCREMENT_INTERVAL = Duration.ofSeconds(DEFAULT_CAPACITY);

    private final Logger logger = LoggingUtils.getLogger(getClass());
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final int initial;
    private final int capacity;
    private final Duration incrementInterval;
    private final Duration acquireInterval;
    private int count;
    private long lastAcquireMs = 0;

    /**
     * A TokenBucket acts as a rate limiting helper.  Clients should not perform rate limited work without acquiring a
     * token from the {@link #tryAcquire()} method.
     *
     * @param builder the configured builder
     */
    protected TokenBucket(Builder builder) {
        this.initial = builder.initial;
        this.count = builder.initial;
        this.capacity = builder.capacity;
        this.incrementInterval = builder.incrementInterval;
        this.acquireInterval = builder.acquireInterval;

        String msg = String.format(
                "Configured with count: %d, capacity: %d, incrementInterval: %ds, acquireInterval: %ds",
                count, capacity, incrementInterval.getSeconds(), acquireInterval.getSeconds());

        logger.info(msg);

        if (count < 0
                || capacity < 1
                || incrementInterval.isNegative()
                || incrementInterval.isZero()
                || acquireInterval.isNegative()) {
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

    public static Builder newBuilder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .initialTokenCount(initial)
                .capacity(capacity)
                .incrementInterval(incrementInterval)
                .acquireInterval(acquireInterval);
    }

    /**
     * This method returns true if a rate-limited action should be executed, and false if the action should not occur.
     */
    public synchronized boolean tryAcquire() {
        if (count > 0 && durationHasPassed(acquireInterval)) {
            count--;
            lastAcquireMs = now();
            return true;
        }

        return false;
    }

    /**
     * Returns the current time in epoch milliseconds. Broken out into a separate function to allow overriding in tests.
     */
    @VisibleForTesting
    protected long now() {
        return System.currentTimeMillis();
    }

    /**
     * Resets internal counters for tests.
     */
    @VisibleForTesting
    public synchronized void reset() {
        count = initial;
        lastAcquireMs = 0;
    }

    /**
     * This method adds a token to the bucket up to the capacity of the bucket.
     */
    private synchronized void increment() {
        if (count < capacity) {
            count++;
        }
    }

    /**
     * This method is used to enforce a minimum duration between granting tokens.  It returns true when that minimum
     * duration has passed and false otherwise.
     */
    private boolean durationHasPassed(Duration duration) {
        return now() - lastAcquireMs >= duration.toMillis();
    }

    /**
     * This class is a builder for {@link TokenBucket}s.
     */
    public static final class Builder {
        protected int initial = DEFAULT_INITIAL_COUNT;
        protected int capacity = DEFAULT_CAPACITY;
        protected Duration incrementInterval = DEFAULT_INCREMENT_INTERVAL;
        protected Duration acquireInterval = DEFAULT_ACQUIRE_INTERVAL;

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
            return new TokenBucket(this);
        }
    }
}
