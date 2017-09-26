package com.mesosphere.sdk.scheduler.plan;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

/**
 * Provides a default implementation of commonly-used {@link Step} logic.
 */
public abstract class AbstractStep implements Step {

    /**
     * Non-static to ensure that we inherit the names of subclasses.
     */
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected UUID id = UUID.randomUUID();
    private final String name;

    final Object statusLock = new Object();
    private boolean interrupted;

    protected AbstractStep(String name) {
        this.name = name;
        this.interrupted = false;
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
            Status internalStatus = getStatusInternal();
            if (interrupted && (internalStatus == Status.PENDING || internalStatus == Status.PREPARED)) {
                return Status.WAITING;
            }

            return internalStatus;
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

    protected abstract Status getStatusInternal();

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
