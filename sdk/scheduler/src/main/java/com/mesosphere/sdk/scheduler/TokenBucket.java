package com.mesosphere.sdk.scheduler;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class implements a token bucket.
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

    public TokenBucket(int count, int capacity, Duration delay) {
        this.count = count;
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

    private synchronized void increment() {
        if (count < capacity) {
            count++;
        }
    }

    public synchronized boolean tryAcquire() {
        if (count > 0 && durationHasPassed(MIN_REVIVE_DELAY)) {
            count--;
            lastRevive = System.currentTimeMillis();
            return true;
        }

        return false;
    }

    private boolean durationHasPassed(Duration duration) {
        return System.currentTimeMillis() - lastRevive > duration.getSeconds();
    }
}
