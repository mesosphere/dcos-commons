package com.mesosphere.sdk.scheduler.plan;

import com.mesosphere.sdk.scheduler.DefaultObservable;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/**
 * Provides a default implementation of commonly-used {@link Step} logic.
 */
public abstract class AbstractStep extends DefaultObservable implements Step {

    /**
     * Non-static to ensure that we inherit the names of subclasses.
     */
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final UUID id = UUID.randomUUID();
    private final String name;

    private final Object statusLock = new Object();
    private Status status;
    private boolean interrupted;

    protected AbstractStep(String name, Status status) {
        this.name = name;
        this.status = status;
        this.interrupted = false;

        setStatus(status); // Log initial status
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Status getStatus() {
        synchronized (statusLock) {
            if (interrupted && (status == Status.PENDING || status == Status.PREPARED)) {
                return Status.WAITING;
            }
            return status;
        }
    }

    /**
     * Updates the status setting and logs the outcome. Should only be called either by tests, by
     * {@code this}, or by subclasses.
     *
     * @param newStatus the new status to be set
     */
    protected void setStatus(Status newStatus) {
        Status oldStatus = status;
        status = newStatus;
        logger.info("{}: changed status from: {} to: {} (interrupted={})",
                getName(), oldStatus, newStatus, interrupted);

        if (!Objects.equals(oldStatus, newStatus)) {
            notifyObservers();
        }
    }

    @Override
    public void interrupt() {
        synchronized (statusLock) {
            interrupted = true;
        }
    }

    @Override
    public void proceed() {
        synchronized (statusLock) {
            interrupted = false;
        }
    }

    @Override
    public boolean isInterrupted() {
        synchronized (statusLock) {
            return interrupted;
        }
    }

    @Override
    public void restart() {
        logger.warn("Restarting step: '{} [{}]'", getName(), getId());
        setStatus(Status.PENDING);
    }

    @Override
    public void forceComplete() {
        logger.warn("Forcing completion of step: '{} [{}]'", getName(), getId());
        setStatus(Status.COMPLETE);
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
