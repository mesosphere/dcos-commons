package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.TextFormat;
import com.mesosphere.sdk.scheduler.decommission.DecommissionPlanFactory;
import com.mesosphere.sdk.scheduler.recovery.FailureUtils;
import com.mesosphere.sdk.state.StateStore;
import com.mesosphere.sdk.state.StateStoreException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The Resource Cleaner provides recommended operations for cleaning up
 * unexpected Reserved resources and persistent volumes.
 */
public class ResourceCleaner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceCleaner.class);

    private final Collection<Resource> expectedResources;
    private final Protos.FrameworkInfo frameworkInfo;

    /**
     * Creates a new {@link ResourceCleaner} which retrieves expected resource
     * information from the provided {@link StateStore}.
     *
     * @throws StateStoreException
     *             if there's a failure when retrieving resource information
     */
    public ResourceCleaner(Protos.FrameworkInfo frameworkInfo, Collection<Resource> expectedResources) {
        this.frameworkInfo = frameworkInfo;
        this.expectedResources = expectedResources;
    }

    /**
     * Returns a list of operations which should be performed, given the provided list of Offers
     * from Mesos. The returned operations MUST be performed in the order in which they are
     * provided.
     */
    public List<OfferRecommendation> evaluate(List<Offer> offers) {
        // ORDERING IS IMPORTANT:
        //    The resource lifecycle is RESERVE -> CREATE -> DESTROY -> UNRESERVE
        //    Therefore we *must* put any DESTROY calls before any UNRESERVE calls
        List<OfferRecommendation> recommendations = new ArrayList<>();

        // First, find any persistent volumes to be DESTROYed
        for (Offer offer : offers) {
            for (Resource persistentVolume : getPersistentVolumesToBeDestroyed(offer)) {
                recommendations.add(new DestroyOfferRecommendation(offer, persistentVolume));
            }
        }

        // Then, find any unexpected persistent volumes AND resource reservations which should
        // (both) be UNRESERVEd
        for (Offer offer : offers) {
            for (Resource reservedResource : getReservedResourcesToBeUnreserved(offer)) {
                recommendations.add(new UnreserveOfferRecommendation(offer, reservedResource));
            }
        }

        return recommendations;
    }

    /**
     * Returns a list of all expected resources, which are extracted from all {@link org.apache.mesos.Protos.TaskInfo}s
     * produced by the provided {@link StateStore}.
     */
    @VisibleForTesting
    public static Collection<Resource> getExpectedResources(StateStore stateStore) throws StateStoreException {
        return stateStore.fetchTasks().stream()
                // The task's resources should be unreserved if:
                // - the task is marked as permanently failed, or
                // - the task is in the process of being decommissioned
                .filter(taskInfo ->
                        !FailureUtils.isPermanentlyFailed(taskInfo) &&
                        !stateStore.fetchGoalOverrideStatus(taskInfo.getName())
                                .equals(DecommissionPlanFactory.DECOMMISSIONING_STATUS))
                .map(ResourceUtils::getAllResources)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * Examines the {@link Offer} to determine which {@link Resource}s should be unreserved.
     *
     * @param offer The {@link Offer} containing the {@link Resource}s.
     * @return A {@link Collection} of {@link Resource}s that should be unreserved.
     */
    private Collection<? extends Resource> getReservedResourcesToBeUnreserved(Offer offer) {
        return selectUnexpectedResources(getReservedResourceIds(expectedResources), getReservedResourcesById(offer));
    }

    /**
     * Examines the {@link Offer} to determine which volume {@link Resource}s should be destroyed.
     *
     * @param offer The {@link Offer} containing the persistent volume {@link Resource}s.
     * @return A {@link Collection} of {@link Resource}s that should be destroyed.
     */
    private Collection<? extends Resource> getPersistentVolumesToBeDestroyed(Offer offer) {
        return selectUnexpectedResources(getPersistentVolumeIds(expectedResources), getPersistentVolumesById(offer));
    }

    /**
     * Returns a list of resources from {@code resourcesById} whose ids are not present in
     * {@code expectedIds}.
     */
    private Collection<Resource> selectUnexpectedResources(
            Set<String> expectedIds, Map<String, Resource> resourcesById) {
        List<Resource> unexpectedResources = new ArrayList<>();

        for (Map.Entry<String, Resource> entry : resourcesById.entrySet()) {
            if (expectedIds.contains(entry.getKey())) {
                continue; // leave resource as-is
            }
            if (ResourceUtils.isOwnedByThisFramework(entry.getValue(), frameworkInfo)) {
                LOGGER.info("Unexpected reserved resource: {}", TextFormat.shortDebugString(entry.getValue()));
                unexpectedResources.add(entry.getValue());
            } else {
                LOGGER.warn("Unexpected resource which is not owned by this framework, leaving as-is: {}",
                        TextFormat.shortDebugString(entry.getValue()));
            }
        }
        return unexpectedResources;
    }

    /**
     * Returns the resource ids for all {@code resources} which represent persistent volumes, or
     * an empty list if no persistent volume resources were found.
     */
    private static Set<String> getPersistentVolumeIds(Collection<Resource> resources) {
        Set<String> persistenceIds = new HashSet<>();
        for (Resource resource : resources) {
            if (ResourceUtils.hasResourceId(resource) && resource.hasDisk() && resource.getDisk().hasPersistence()) {
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
        resourceIds.addAll(ResourceUtils.getResourceIds(resources));
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
            if (ResourceUtils.hasResourceId(resource) && resource.hasDisk() && resource.getDisk().hasPersistence()) {
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
            Optional<String> resourceId = ResourceUtils.getResourceId(resource);
            if (resourceId.isPresent()) {
                reservedResources.put(resourceId.get(), resource);
            }
        }
        return reservedResources;
    }
}
