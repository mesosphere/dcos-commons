package com.mesosphere.sdk.scheduler;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a token bucket to limit the rate at which actions may be taken.  The assumption is that clients
 * will not take actions unless they receive a token via the {@link #tryAcquire()} method.
 */
public class TokenBucket {
    public static final int REVIVE_CAPACITY = 256;
    public static final int INITIAL_REVIVE_COUNT = REVIVE_CAPACITY;
    public static final Duration MIN_REVIVE_DELAY = Duration.ofSeconds(5);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final int capacity;
    private final Duration delay;
    private int count;
    private long lastRevive = System.currentTimeMillis();

    public TokenBucket() {
        this(INITIAL_REVIVE_COUNT, REVIVE_CAPACITY, Duration.ofSeconds(REVIVE_CAPACITY));
    }

    /**
     * A TokenBucket acts as a rate limiting helper.  Clients should not perform rate limited work without acquiring a
     * token from the {@link #tryAcquire()} method.
     * @param initial The initial number of tokens available
     * @param capacity The maximum number of tokens the bucket may hold
     * @param delay The delay between adding a new token to the bucket
     */
    public TokenBucket(int initial, int capacity, Duration delay) {
        this.count = initial;
        this.capacity = capacity;
        this.delay = delay;

        executor.scheduleAtFixedRate(
                new Runnable() {
                    @Override
                    public void run() {
                        increment();
                    }
                },
                0,
                delay.getSeconds(),
                TimeUnit.SECONDS);
    }

    /**
     * This method returns true if a rate-limited action should be executed, and false if the action should not occur.
     */
    public synchronized boolean tryAcquire() {
        if (count > 0 && durationHasPassed(MIN_REVIVE_DELAY)) {
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
     * This method is used to enforce a minimum delay between granting tokens.  It returns true when that minimum
     * duration has passed and false otherwise.
     */
    private boolean durationHasPassed(Duration duration) {
        return System.currentTimeMillis() - lastRevive > duration.getSeconds();
    }
}
