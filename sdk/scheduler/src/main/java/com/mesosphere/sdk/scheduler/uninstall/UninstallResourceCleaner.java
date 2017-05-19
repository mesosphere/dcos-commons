package com.mesosphere.sdk.scheduler.uninstall;

import com.mesosphere.sdk.offer.ResourceCleaner;
import com.mesosphere.sdk.offer.ResourceCollectUtils;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The Uninstall Resource Cleaner provides recommended operations for cleaning up
 * all Reserved resources and persistent volumes.
 */
public class UninstallResourceCleaner implements ResourceCleaner {
    private static final Logger logger = LoggerFactory.getLogger(UninstallResourceCleaner.class);

    /**
     * Examines the {@link Offer} to determine which reserved {@link Resource}s have resource ID's and therefore
     * should be unreserved.
     *
     * @param offer The {@link Offer} containing the {@link Resource}s.
     * @return A {@link Collection} of {@link Resource}s that should be unreserved.
     */
    @Override
    public Collection<Resource> getReservedResourcesToBeUnreserved(Offer offer) {
        List<Resource> resources = offer.getResourcesList().stream()
                .filter(resource -> ResourceCollectUtils.getResourceId(resource).isPresent())
                .collect(Collectors.toList());
        logger.info("Reserved resources to be unreserved: {}", resources);
        return resources;
    }

    /**
     * Examines the {@link Offer} to determine which volume {@link Resource}s have resource ID's and therefore
     * should be destroyed.
     *
     * @param offer The {@link Offer} containing the persistent volume {@link Resource}s.
     * @return A {@link Collection} of {@link Resource}s that should be destroyed.
     */
    @Override
    public Collection<Resource> getPersistentVolumesToBeDestroyed(Offer offer) {
        List<Resource> resources = offer.getResourcesList().stream()
                .filter(resource -> resource.hasDisk() && resource.getDisk().hasPersistence())
                .collect(Collectors.toList());
        logger.info("Persistent volumes to be destroyed and unreserved: {}", resources);
        return resources;
    }
}
