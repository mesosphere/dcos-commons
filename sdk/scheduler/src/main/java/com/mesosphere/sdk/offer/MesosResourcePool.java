package com.mesosphere.sdk.offer;

import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A representation of the pool of resources available in a single {@link Offer}. Tracks the
 * consumption of the {@link Offer}'s resources, as they are matched with
 * {@link ResourceRequirement}s.
 */
public class MesosResourcePool {
    private static final Logger logger = LoggerFactory.getLogger(MesosResourcePool.class);

    private Offer offer;
    private Map<String, List<MesosResource>> unreservedAtomicPool;
    private Map<String, Value> unreservedMergedPool;
    private Map<String, MesosResource> reservedPool;

    /**
     * Creates a new pool of resources based on what's available in the provided {@link Offer}.
     */
    public MesosResourcePool(Offer offer) {
        init(offer);
    }

    private void init(Offer offer) {
        this.offer = offer;
        final Collection<MesosResource> mesosResources = getMesosResources(offer);
        this.unreservedAtomicPool = getUnreservedAtomicPool(mesosResources);
        this.unreservedMergedPool = getUnreservedMergedPool(mesosResources);
        this.reservedPool = getReservedPool(mesosResources);
    }

    /**
     * Returns the underlying offer which this resource pool represents.
     */
    public Offer getOffer() {
        return offer;
    }

    /**
     * Returns the unreserved resources which cannot be partially consumed from an Offer. For
     * example, a MOUNT volume cannot be partially consumed, it's all-or-nothing.
     */
    public Map<String, List<MesosResource>> getUnreservedAtomicPool() {
        return unreservedAtomicPool;
    }

    /**
     * Returns the unreserved resources of which a subset can be consumed from an Offer. For
     * example, an offer may contain 4.0 CPUs and 2.4 of those CPUs can be reserved.
     */
    public Map<String, Value> getUnreservedMergedPool() {
        return unreservedMergedPool;
    }

    /**
     * Returns the resources which are reserved.
     */
    public Map<String, MesosResource> getReservedPool() {
        return reservedPool;
    }

    /**
     * Returns the reserved resource, if present.
     */
    public Optional<MesosResource> getReservedResourceById(String resourceId) {
        return Optional.ofNullable(reservedPool.get(resourceId));
    }

    /**
     * Consumes and returns a {@link MesosResource} which meets the provided
     * {@link ResourceRequirement}, or does nothing and returns an empty {@link Optional} if no
     * available resources meet the requirement.
     */
    public Optional<MesosResource> consume(ResourceRequirement resourceRequirement) {
        if (resourceRequirement.expectsResource()) {
            return consumeReserved(resourceRequirement);
        } else if (resourceRequirement.isAtomic()) {
            return consumeAtomic(resourceRequirement);
        } else if (resourceRequirement.reservesResource() || resourceRequirement.consumesUnreservedResource()) {
            return consumeUnreservedMerged(resourceRequirement);
        }

        logger.error("The following resource requirement did not meet any consumption criteria: {}",
                TextFormat.shortDebugString(resourceRequirement.getResource()));
        return Optional.empty();
    }

    /**
     * Update the offer this pool represents, re-calculating available unreserved, reserved and atomic resources.
     * @param offer the offer to encapsulate
     */
    public void update(Offer offer) {
        init(offer);
    }

    /**
     * Marks the provided resource as available for consumption.
     */
    public void release(MesosResource mesosResource) {
        if (mesosResource.isAtomic()) {
            releaseAtomicResource(mesosResource);
            return;
        } else {
            releaseMergedResource(mesosResource);
            return;
        }
    }

    private void releaseMergedResource(MesosResource mesosResource) {
        Value currValue = unreservedMergedPool.get(mesosResource.getName());

        if (currValue == null) {
            currValue = ValueUtils.getZero(mesosResource.getType());
        }

        Value updatedValue = ValueUtils.add(currValue, mesosResource.getValue());
        unreservedMergedPool.put(mesosResource.getName(), updatedValue);
    }

    private void releaseAtomicResource(MesosResource mesosResource) {
        Resource.Builder resBuilder = Resource.newBuilder(mesosResource.getResource());
        resBuilder.clearReservation();
        resBuilder.setRole(Constants.ANY_ROLE);

        if (resBuilder.hasDisk()) {
            DiskInfo.Builder diskBuilder = DiskInfo.newBuilder(resBuilder.getDisk());
            diskBuilder.clearPersistence();
            diskBuilder.clearVolume();
            resBuilder.setDisk(diskBuilder.build());
        }

        Resource releasedResource = resBuilder.build();

        List<MesosResource> resList = unreservedAtomicPool.get(mesosResource.getName());
        if (resList == null) {
            resList = new ArrayList<MesosResource>();
        }

        resList.add(new MesosResource(releasedResource));
        unreservedAtomicPool.put(mesosResource.getName(), resList);
    }

    private Optional<MesosResource> consumeReserved(ResourceRequirement resourceRequirement) {
        String resourceId = resourceRequirement.getResourceId().get();
        MesosResource mesosResource = reservedPool.get(resourceId);
        if (mesosResource == null) {
            logger.warn("Failed to find reserved {} resource with ID: {}. Reserved resource IDs are: {}",
                    resourceRequirement.getName(), resourceId, reservedPool.keySet());
            return Optional.empty();
        }

        if (mesosResource.isAtomic()) {
            if (sufficientValue(resourceRequirement.getValue(), mesosResource.getValue())) {
                reservedPool.remove(resourceId);
            } else {
                logger.warn("Reserved atomic quantity of {} is insufficient: desired {}, reserved {}",
                        resourceRequirement.getName(),
                        TextFormat.shortDebugString(resourceRequirement.getValue()),
                        TextFormat.shortDebugString(mesosResource.getValue()));
                return Optional.empty();
            }
        } else {
            Value desiredValue = resourceRequirement.getValue();
            Value availableValue = reservedPool.get(resourceId).getValue();
            if (ValueUtils.compare(availableValue, desiredValue) > 0) {
                // update the value in pool with the remaining unclaimed resource amount
                Resource remaining = ResourceBuilder.fromExistingResource(mesosResource.getResource())
                        .setValue(ValueUtils.subtract(availableValue, desiredValue))
                        .build();
                reservedPool.put(resourceId, new MesosResource(remaining));
                // return only the claimed resource amount from this reservation
                mesosResource = new MesosResource(resourceRequirement.getResource());
            } else {
                reservedPool.remove(resourceId);
            }
        }

        return Optional.ofNullable(mesosResource);
    }

    private Optional<MesosResource> consumeAtomic(ResourceRequirement resourceRequirement) {
        Value desiredValue = resourceRequirement.getValue();
        List<MesosResource> atomicResources = unreservedAtomicPool.get(resourceRequirement.getName());
        List<MesosResource> filteredResources = new ArrayList<>();
        Optional<MesosResource> sufficientResource = Optional.empty();

        if (atomicResources != null) {
            for (MesosResource atomicResource : atomicResources) {
                if (!sufficientResource.isPresent() && sufficientValue(desiredValue, atomicResource.getValue())) {
                    sufficientResource = Optional.of(atomicResource);
                    // do NOT break: ensure filteredResources is fully populated
                } else {
                    filteredResources.add(atomicResource);
                }
            }
        }

        if (filteredResources.isEmpty()) {
            unreservedAtomicPool.remove(resourceRequirement.getName());
        } else {
            unreservedAtomicPool.put(resourceRequirement.getName(), filteredResources);
        }

        if (!sufficientResource.isPresent()) {
            if (atomicResources == null) {
                logger.info("Offer lacks any atomic resources named {}", resourceRequirement.getName());
            } else {
                logger.info("Offered quantity in all {} instances of {} is insufficient: desired {}",
                        atomicResources.size(),
                        resourceRequirement.getName(),
                        TextFormat.shortDebugString(resourceRequirement.getResource()));
            }
        }

        return sufficientResource;
    }

    private Optional<MesosResource> consumeUnreservedMerged(ResourceRequirement resourceRequirement) {
        Value desiredValue = resourceRequirement.getValue();
        Value availableValue = unreservedMergedPool.get(resourceRequirement.getName());

        if (sufficientValue(desiredValue, availableValue)) {
            unreservedMergedPool.put(resourceRequirement.getName(), ValueUtils.subtract(availableValue, desiredValue));
            return Optional.of(new MesosResource(
                    ResourceBuilder.fromUnreservedValue(resourceRequirement.getName(), desiredValue).build()));
        } else {
            if (availableValue == null) {
                logger.info("Offer lacks any resources named {}", resourceRequirement.getName());
            } else {
                logger.info("Offered quantity of {} is insufficient: desired {}, offered {}",
                        resourceRequirement.getName(),
                        TextFormat.shortDebugString(desiredValue),
                        TextFormat.shortDebugString(availableValue));
            }
            return Optional.empty();
        }
    }

    private static boolean sufficientValue(Value desired, Value available) {
        if (desired == null) {
            return true;
        } else if (available == null) {
            return false;
        }

        Value difference = ValueUtils.subtract(desired, available);
        return ValueUtils.compare(difference, ValueUtils.getZero(desired.getType())) <= 0;
    }

    private static Collection<MesosResource> getMesosResources(Offer offer) {
        Collection<MesosResource> mesosResources = new ArrayList<MesosResource>();

        for (Resource resource : offer.getResourcesList()) {
            mesosResources.add(new MesosResource(resource));
        }

        return mesosResources;
    }

    private static Map<String, MesosResource> getReservedPool(
            Collection<MesosResource> mesosResources) {
        Map<String, MesosResource> reservedPool = new HashMap<String, MesosResource>();

        for (MesosResource mesResource : mesosResources) {
            Optional<String> resourceId = mesResource.getResourceId();
            if (resourceId.isPresent()) {
                reservedPool.put(resourceId.get(), mesResource);
            }
        }

        return reservedPool;
    }

    private static Map<String, List<MesosResource>> getUnreservedAtomicPool(
            Collection<MesosResource> mesosResources) {
        Map<String, List<MesosResource>> pool = new HashMap<String, List<MesosResource>>();

        for (MesosResource mesosResource : getUnreservedAtomicResources(mesosResources)) {
            String name = mesosResource.getName();
            List<MesosResource> resList = pool.get(name);

            if (resList == null) {
                resList = new ArrayList<MesosResource>();
            }

            resList.add(mesosResource);
            pool.put(name, resList);
        }

        return pool;
    }

    private static Map<String, Value> getUnreservedMergedPool(
            Collection<MesosResource> mesosResources) {
        Map<String, Value> pool = new HashMap<String, Value>();

        for (MesosResource mesosResource : getUnreservedMergedResources(mesosResources)) {
            String name = mesosResource.getName();
            Value currValue = pool.get(name);

            if (currValue == null) {
                currValue = ValueUtils.getZero(mesosResource.getType());
            }

            pool.put(name, ValueUtils.add(currValue, mesosResource.getValue()));
        }

        return pool;
    }

    private static Collection<MesosResource> getUnreservedAtomicResources(
            Collection<MesosResource> mesosResources) {
        return getUnreservedResources(getAtomicResources(mesosResources));
    }

    private static Collection<MesosResource> getUnreservedMergedResources(
            Collection<MesosResource> mesosResources) {
        return getUnreservedResources(getMergedResources(mesosResources));
    }

    private static Collection<MesosResource> getUnreservedResources(
            Collection<MesosResource> mesosResources) {
        Collection<MesosResource> unreservedResources = new ArrayList<MesosResource>();

        for (MesosResource mesosResource : mesosResources) {
            if (!mesosResource.getResourceId().isPresent()) {
                unreservedResources.add(mesosResource);
            }
        }

        return unreservedResources;
    }

    private static Collection<MesosResource> getAtomicResources(
            Collection<MesosResource> mesosResources) {
        Collection<MesosResource> atomicResources = new ArrayList<>();

        for (MesosResource mesosResource : mesosResources) {
            if (mesosResource.isAtomic()) {
                atomicResources.add(mesosResource);
            }
        }

        return atomicResources;
    }

    private static Collection<MesosResource> getMergedResources(
            Collection<MesosResource> mesosResources) {
        Collection<MesosResource> mergedResources = new ArrayList<>();

        for (MesosResource mesosResource : mesosResources) {
            if (!mesosResource.isAtomic()) {
                mergedResources.add(mesosResource);
            }
        }

        return mergedResources;
    }
}
