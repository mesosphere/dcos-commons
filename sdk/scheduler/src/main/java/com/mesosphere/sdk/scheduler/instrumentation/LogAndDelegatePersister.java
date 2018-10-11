package com.mesosphere.sdk.scheduler.instrumentation;

import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

/**
 * Logs each operation and delegates to the actual persister.
 */
public class LogAndDelegatePersister implements Persister {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogAndDelegatePersister.class);
    private final Persister targetPersister;

    public LogAndDelegatePersister(Persister targetPersister) {
        this.targetPersister = targetPersister;
    }

    private void log(String methodName) {
        LOGGER.info("PERSISTER CALL: {}", methodName);
    }

    private void log(String methodName, String extraInfo) {
        LOGGER.info("PERSISTER CALL: {}({})", methodName, extraInfo);
    }

    @Override
    public byte[] get(String unprefixedPath) throws PersisterException {
        log("get", unprefixedPath);
        return targetPersister.get(unprefixedPath);
    }

    @Override
    public Collection<String> getChildren(String unprefixedPath) throws PersisterException {
        log("getChildren", unprefixedPath);
        return targetPersister.getChildren(unprefixedPath);
    }

    @Override
    public void recursiveDelete(String unprefixedPath) throws PersisterException {
        log("recursiveDelete", unprefixedPath);
        targetPersister.recursiveDelete(unprefixedPath);
    }

    @Override
    public void set(String unprefixedPath, byte[] bytes) throws PersisterException {
        log("set", unprefixedPath);
        targetPersister.set(unprefixedPath, bytes);
    }

    @Override
    public void setMany(Map<String, byte[]> unprefixedPathBytesMap) throws PersisterException {
        log("setMany", String.format("%d paths", unprefixedPathBytesMap.size()));
        targetPersister.setMany(unprefixedPathBytesMap);
    }

    @Override
    public void recursiveDeleteMany(Collection<String> unprefixedPaths) throws PersisterException {
        log("recursiveDeleteMany", String.format("%d paths", unprefixedPaths));
        targetPersister.recursiveDeleteMany(unprefixedPaths);
    }

    @Override
    public Map<String, byte[]> getMany(Collection<String> unprefixedPaths) throws PersisterException {
        log("getMany", String.format("%d paths", unprefixedPaths));
        return targetPersister.getMany(unprefixedPaths);
    }

    @Override
    public void close() {
        log("close");
        targetPersister.close();
    }
}
