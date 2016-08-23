package org.apache.mesos.executor;

/**
 * This class encapsulates the relevant statistics associated with a single HealthCheck.
 */
public class HealthCheckStats {
    private final String name;
    private long totalFailures = 0;
    private long totalSuccesses = 0;
    private long consecutiveFailures = 0;
    private long consecutiveSuccesses = 0;

    public HealthCheckStats(String name) {
        this.name = name;
    }

    public void failed() {
        totalFailures++;
        consecutiveFailures++;
        consecutiveSuccesses = 0;
    }

    public void succeeded() {
        totalSuccesses++;
        consecutiveSuccesses++;
        consecutiveFailures = 0;
    }

    public String getName() {
        return name;
    }

    public long getTotalFailures() {
        return totalFailures;
    }

    public long getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public long getTotalSuccesses() {
        return totalSuccesses;
    }


    public long getConsecutiveSuccesses() {
        return consecutiveSuccesses;
    }

    @Override
    public String toString() {
        return "HealthCheckStats{" +
                "name='" + name + '\'' +
                ", totalFailures=" + totalFailures +
                ", totalSuccesses=" + totalSuccesses +
                ", consecutiveFailures=" + consecutiveFailures +
                ", consecutiveSuccesses=" + consecutiveSuccesses +
                '}';
    }
}
