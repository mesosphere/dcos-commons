package com.mesosphere.sdk.offer;

import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;

import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The Resource Cleaner provides recommended operations for cleaning up
 * unexpected Reserved resources and persistent volumes.
 */
public class DefaultResourceCleaner implements ResourceCleaner {
    private static final Logger logger = LoggerFactory.getLogger(DefaultResourceCleaner.class);

    // Only Persistent Volumes are DESTROYed
    private final Set<String> expectedPersistentVolumeIds;
    // Both Persistent Volumes AND Reserved Resources are UNRESERVEd
    private final Set<String> expectedReservedResourceIds;

    /**
     * Creates a new {@link DefaultResourceCleaner} which retrieves expected resource
     * information from the provided {@link StateStore}.
     *
     * @throws StateStoreException
     *             if there's a failure when retrieving resource information
     */
    public DefaultResourceCleaner(StateStore stateStore) {
        Collection<Resource> expectedResources = getExpectedResources(stateStore);
        this.expectedPersistentVolumeIds = getPersistentVolumeIds(expectedResources);
        this.expectedReservedResourceIds = getReservedResourceIds(expectedResources);
    }

    /**
     * Examines the {@link Offer} to determine which {@link Resource}s should be unreserved.
     *
     * @param offer The {@link Offer} containing the {@link Resource}s.
     * @return A {@link Collection} of {@link Resource}s that should be unreserved.
     */
    @Override
    public Collection<? extends Resource> getReservedResourcesToBeUnreserved(Offer offer) {
        return selectUnexpectedResources(expectedReservedResourceIds, getReservedResourcesById(offer));
    }

    /**
     * Examines the {@link Offer} to determine which volume {@link Resource}s should be destroyed.
     *
     * @param offer The {@link Offer} containing the persistent volume {@link Resource}s.
     * @return A {@link Collection} of {@link Resource}s that should be destroyed.
     */
    @Override
    public Collection<? extends Resource> getPersistentVolumesToBeDestroyed(Offer offer) {
        return selectUnexpectedResources(expectedPersistentVolumeIds, getPersistentVolumesById(offer));
    }

    /**
     * Returns a list of resources from {@code resourcesById} whose ids are not present in
     * {@code expectedIds}.
     */
    private static Collection<Resource> selectUnexpectedResources(
            Set<String> expectedIds, Map<String, Resource> resourcesById) {
        List<Resource> unexpectedResources = new ArrayList<>();

        for (Map.Entry<String, Resource> entry : resourcesById.entrySet()) {
            if (!expectedIds.contains(entry.getKey())) {
                logger.info("Unexpected reserved resource found: {}", entry);
                unexpectedResources.add(entry.getValue());
            }
        }
        return unexpectedResources;
    }

    /**
     * Returns a list of all expected resources, which are extracted from all {@link Protos.TaskInfo}s
     * produced by the provided {@link StateStore}.
     */
    private static Collection<Resource> getExpectedResources(StateStore stateStore) throws StateStoreException {
        return stateStore.fetchTasks().stream()
                .filter(taskInfo -> !FailureUtils.isLabeledAsFailed(taskInfo))
                .map(ResourceCollectUtils::getAllResources)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * Returns the resource ids for all {@code resources} which represent persistent volumes, or
     * an empty list if no persistent volume resources were found.
     */
    private static Set<String> getPersistentVolumeIds(Collection<Resource> resources) {
        Set<String> persistenceIds = new HashSet<>();
        for (Resource resource : resources) {
            if (resource.hasDisk() && resource.getDisk().hasPersistence()) {
                persistenceIds.add(resource.getDisk().getPersistence().getId());
            }
        }
        return persistenceIds;
    }

    /**
     * Returns the resource ids for all {@code resources} which represent reserved resources, or
     * an empty list if no reserved resources were found.
     */
    private static Set<String> getReservedResourceIds(Collection<Resource> resources) {
        Set<String> resourceIds = new HashSet<>();
        resourceIds.addAll(ResourceCollectUtils.getResourceIds(resources));
        return resourceIds;
    }

    /**
     * Returns an ID -> Resource mapping of all disk resources listed in the provided {@link Offer},
     * or an empty list of no disk resources are found.
     * @param offer The Offer being deconstructed.
     * @return The map of resources from the {@link Offer}
     */
    private static Map<String, Resource> getPersistentVolumesById(Offer offer) {
        Map<String, Resource> volumes = new HashMap<>();
        for (Resource resource : offer.getResourcesList()) {
            if (resource.hasDisk() && resource.getDisk().hasPersistence()) {
                volumes.put(resource.getDisk().getPersistence().getId(), resource);
            }
        }
        return volumes;
    }

    /**
     * Returns an ID -> Resource mapping of all reservation resources listed in the provided
     * {@link Offer}, or an empty list if no reservation resources are found.
     */
    private static Map<String, Resource> getReservedResourcesById(Offer offer) {
        Map<String, Resource> reservedResources = new HashMap<>();
        for (Resource resource : offer.getResourcesList()) {
            Optional<String> resourceId = ResourceCollectUtils.getResourceId(resource);
            if (resourceId.isPresent()) {
                reservedResources.put(resourceId.get(), resource);
            }
        }
        return reservedResources;
    }
}
