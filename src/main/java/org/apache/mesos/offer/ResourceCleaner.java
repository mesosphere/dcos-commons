package org.apache.mesos.offer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.state.StateStore;
import org.apache.mesos.state.StateStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Resource Cleaner provides recommended operations for cleaning up
 * unexpected Reserved resources and persistent volumes.
 */
public class ResourceCleaner {
    private static final Logger logger = LoggerFactory.getLogger(ResourceCleaner.class);

    // Only Persistent Volumes are DESTROYed
    private final Set<String> expectedPersistentVolumeIds;
    // Both Persistent Volumes AND Reserved Resources are UNRESERVEd
    private final Set<String> expectedReservedResourceIds;

    /**
     * Creates a new {@link ResourceCleaner} which retrieves expected resource
     * information from the provided {@link StateStore}.
     *
     * @throws StateStoreException
     *             if there's a failure when retrieving resource information
     */
    public ResourceCleaner(StateStore stateStore) {
        this(getExpectedResources(stateStore));
    }

    /**
     * Creates a new {@link ResourceCleaner} using the provided expected resource information.
     *
     * @param expectedResources a list of all expected resources
     */
    public ResourceCleaner(Collection<Resource> expectedResources) {
        this.expectedPersistentVolumeIds = getPersistentVolumeIds(expectedResources);
        this.expectedReservedResourceIds = getReservedResourceIds(expectedResources);
    }

    /**
     * Returns a list of operations which should be performed, given the provided list of Offers
     * from Mesos. The returned operations MUST be performed in the order in which they are
     * provided.
     */
    public List<OfferRecommendation> evaluate(List<Offer> offers) {
        // ORDERING IS IMPORTANT:
        //    The resource lifecycle is RESERVE -> CREATE -> DESTROY -> UNRESERVE
        // Therefore we *must* put any DESTROY calls before any UNRESERVE calls
        List<OfferRecommendation> recommendations = new ArrayList<OfferRecommendation>();

        // First, find any unexpected persistent volumes which should be DESTROYed
        int offerResourceCount = 0;
        for (Offer offer : offers) {
            offerResourceCount += offer.getResourcesCount();
            for (Resource toDestroy : selectUnexpectedResources(
                    expectedPersistentVolumeIds, getPersistentVolumesById(offer))) {
                recommendations.add(new DestroyOfferRecommendation(offer, toDestroy));
            }
        }
        int destroyRecommendationCount = recommendations.size();

        // Then, find any unexpected persistent volumes AND resource reservations which should
        // (both) be UNRESERVEd
        for (Offer offer : offers) {
            for (Resource toUnreserve : selectUnexpectedResources(
                    expectedReservedResourceIds, getReservedResourcesById(offer))) {
                recommendations.add(new UnreserveOfferRecommendation(offer, toUnreserve));
            }
        }

        logger.info("{} offers with {} resources => {} destroy and {} unreserve operations",
                offers.size(), offerResourceCount,
                destroyRecommendationCount,
                recommendations.size() - destroyRecommendationCount);
        return recommendations;
    }

    /**
     * Returns a list of resources from {@code resourcesById} whose ids are not present in
     * {@code expectedIds}.
     */
    private static Collection<Resource> selectUnexpectedResources(
            Set<String> expectedIds, Map<String, Resource> resourcesById) {
        List<Resource> unexpectedResources = new ArrayList<Resource>();

        for (Map.Entry<String, Resource> entry : resourcesById.entrySet()) {
            if (!expectedIds.contains(entry.getKey())) {
                unexpectedResources.add(entry.getValue());
            }
        }

        return unexpectedResources;
    }

    /**
     * Returns a list of all expected resources, which are extracted from all {@link TaskInfo}s
     * produced by the provided {@link StateStore}.
     */
    private static Collection<Resource> getExpectedResources(StateStore stateStore)
            throws StateStoreException {
        Collection<Resource> resources = new ArrayList<>();

        for (Protos.TaskInfo taskInfo : stateStore.fetchTasks()) {
            // get all resources from both the task level and the executor level
            resources.addAll(taskInfo.getResourcesList());
            if (taskInfo.hasExecutor()) {
                resources.addAll(taskInfo.getExecutor().getResourcesList());
            }
        }

        return resources;
    }

    /**
     * Returns the resource ids for all {@code resources} which represent persistent volumes, or
     * an empty list if no persistent volume resources were found.
     */
    private static Set<String> getPersistentVolumeIds(Collection<Resource> resources) {
        Set<String> persistenceIds = new HashSet<>();

        for (Resource resource : resources) {
            String persistenceId = ResourceUtils.getPersistenceId(resource);
            if (persistenceId != null) {
                persistenceIds.add(persistenceId);
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

        for (Resource resource : resources) {
            String resourceId = ResourceUtils.getResourceId(resource);
            if (resourceId != null) {
                resourceIds.add(resourceId);
            }
        }

        return resourceIds;
    }

    /**
     * Returns an ID -> Resource mapping of all disk resources listed in the provided {@link Offer},
     * or an empty list of no disk resources are found.
     * @param offer
     * @return
     */
    private static Map<String, Resource> getPersistentVolumesById(Offer offer) {
        Map<String, Resource> volumes = new HashMap<String, Resource>();

        for (Resource resource : offer.getResourcesList()) {
            String persistenceId = ResourceUtils.getPersistenceId(resource);
            if (persistenceId != null) {
                volumes.put(persistenceId, resource);
            }
        }

        return volumes;
    }

    /**
     * Returns an ID -> Resource mapping of all reservation resources listed in the provided
     * {@link Offer}, or an empty list if no reservation resources are found.
     */
    private static Map<String, Resource> getReservedResourcesById(Offer offer) {
        Map<String, Resource> reservedResources = new HashMap<String, Resource>();

        for (Resource resource : offer.getResourcesList()) {
            String resourceId = ResourceUtils.getResourceId(resource);
            if (resourceId != null) {
                reservedResources.put(resourceId, resource);
            }
        }

        return reservedResources;
    }
}
