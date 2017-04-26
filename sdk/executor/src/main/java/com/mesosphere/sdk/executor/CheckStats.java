package com.mesosphere.sdk.executor;

/**
 * This class encapsulates the relevant statistics associated with a single HealthCheck.
 */
public class CheckStats {
    private final String name;

    private Object failureLock = new Object();
    private long totalFailures = 0;
    private long consecutiveFailures = 0;

    private Object successLock = new Object();
    private long totalSuccesses = 0;
    private long consecutiveSuccesses = 0;

    public CheckStats(String name) {
        this.name = name;
    }

    public void failed() {
        synchronized (failureLock) {
            totalFailures++;
            consecutiveFailures++;
        }

        synchronized (successLock) {
            consecutiveSuccesses = 0;
        }
    }

    public void succeeded() {
        synchronized (successLock) {
            totalSuccesses++;
            consecutiveSuccesses++;
        }

        synchronized (failureLock) {
            consecutiveFailures = 0;
        }
    }

    public String getName() {
        return name;
    }

    public long getTotalFailures() {
        synchronized (failureLock) {
            return totalFailures;
        }
    }

    public long getConsecutiveFailures() {
        synchronized (failureLock) {
            return consecutiveFailures;
        }
    }

    public long getTotalSuccesses() {
        synchronized (successLock) {
            return totalSuccesses;
        }
    }


    public long getConsecutiveSuccesses() {
        synchronized (successLock) {
            return consecutiveSuccesses;
        }
    }

    @Override
    public String toString() {
        return "CheckStats{" +
                "name='" + name + '\'' +
                ", totalFailures=" + totalFailures +
                ", totalSuccesses=" + totalSuccesses +
                ", consecutiveFailures=" + consecutiveFailures +
                ", consecutiveSuccesses=" + consecutiveSuccesses +
                '}';
    }
}
