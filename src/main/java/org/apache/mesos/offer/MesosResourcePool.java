package org.apache.mesos.offer;

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

    private final Offer offer;
    private final Map<String, List<MesosResource>> unreservedAtomicPool;
    private final Map<String, Value> unreservedMergedPool;
    private final Map<String, MesosResource> reservedPool;

    /**
     * Creates a new pool of resources based on what's available in the provided {@link Offer}.
     */
    public MesosResourcePool(Offer offer) {
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
     * Consumes and returns a {@link MesosResource} which meets the provided
     * {@link ResourceRequirement}, or does nothing and returns an empty {@link Optional} if no
     * available resources meet the requirement.
     */
    public Optional<MesosResource> consume(ResourceRequirement resReq) {
        if (resReq.expectsResource()) {
            logger.info("Retrieving reserved resource");
            return consumeReserved(resReq);
        } else if (resReq.isAtomic()) {
            logger.info("Retrieving atomic resource");
            return consumeAtomic(resReq);
        } else if (resReq.reservesResource()) {
            logger.info("Retrieving resource for reservation");
            return consumeUnreservedMerged(resReq);
        } else if (resReq.consumesUnreservedResource()) {
            logger.info("Retrieving resource for unreserved resource requirement.");
            return consumeUnreservedMerged(resReq);
        }

        logger.error("The following resource requirement did not meet any consumption criteria: {}",
                TextFormat.shortDebugString(resReq.getResource()));
        return Optional.empty();
    }

    /**
     * Consumes and returns a {@link MesosResource} which meets the provided
     * {@link DynamicPortRequirement}, or does nothing and returns an empty {@link Optional} if no
     * available resources meet the requirement.
     */
    public Optional<MesosResource> consume(DynamicPortRequirement dynamicPortRequirement) {
        Value availableValue = unreservedMergedPool.get(dynamicPortRequirement.getName());

        if (availableValue == null) {
            return Optional.empty();
        }

        // Choose first available port
        if (availableValue.getRanges().getRangeCount() > 0) {
            Value.Range range = availableValue.getRanges().getRange(0);
            Resource resource = ResourceUtils.getUnreservedResource(
                    dynamicPortRequirement.getName(),
                    Value.newBuilder()
                        .setType(Value.Type.RANGES)
                        .setRanges(Value.Ranges.newBuilder()
                                .addRange(Value.Range.newBuilder()
                                        .setBegin(range.getBegin())
                                        // Use getBegin again, since we just want the one port.
                                        .setEnd(range.getBegin())))
                        .build());

            return consumeUnreservedMerged(new ResourceRequirement(resource));
        }

        return Optional.empty();
    }

    /**
     * Marks the provided resource as available for consumption.
     */
    public void release(MesosResource mesRes) {
        if (mesRes.isAtomic()) {
            releaseAtomicResource(mesRes);
            return;
        } else {
            releaseMergedResource(mesRes);
            return;
        }
    }

    private void releaseMergedResource(MesosResource mesRes) {
        Value currValue = unreservedMergedPool.get(mesRes.getName());

        if (currValue == null) {
            currValue = ValueUtils.getZero(mesRes.getType());
        }

        Value updatedValue = ValueUtils.add(currValue, mesRes.getValue());
        unreservedMergedPool.put(mesRes.getName(), updatedValue);
    }

    private void releaseAtomicResource(MesosResource mesRes) {
        Resource.Builder resBuilder = Resource.newBuilder(mesRes.getResource());
        resBuilder.clearReservation();
        resBuilder.setRole("*");

        if (resBuilder.hasDisk()) {
            DiskInfo.Builder diskBuilder = DiskInfo.newBuilder(resBuilder.getDisk());
            diskBuilder.clearPersistence();
            diskBuilder.clearVolume();
            resBuilder.setDisk(diskBuilder.build());
        }

        Resource releasedResource = resBuilder.build();

        List<MesosResource> resList = unreservedAtomicPool.get(mesRes.getName());
        if (resList == null) {
            resList = new ArrayList<MesosResource>();
        }

        resList.add(new MesosResource(releasedResource));
        unreservedAtomicPool.put(mesRes.getName(), resList);
    }

    private Optional<MesosResource> consumeReserved(ResourceRequirement resReq) {
        MesosResource mesRes = reservedPool.get(resReq.getResourceId());

        if (mesRes != null) {
            if (mesRes.isAtomic()) {
                if (sufficientValue(resReq.getValue(), mesRes.getValue())) {
                    reservedPool.remove(resReq.getResourceId());
                } else {
                    return Optional.empty();
                }
            } else {
                reservedPool.remove(resReq.getResourceId());
            }
        }

        return Optional.of(mesRes);
    }

    private Optional<MesosResource> consumeAtomic(ResourceRequirement resReq) {
        Value desiredValue = resReq.getValue();
        List<MesosResource> atomicResources = unreservedAtomicPool.get(resReq.getName());
        List<MesosResource> filteredResources = new ArrayList<>();
        Optional<MesosResource> sufficientResource = Optional.empty();

        if (atomicResources != null) {
            for (MesosResource mesRes : atomicResources) {
                if (sufficientValue(desiredValue, mesRes.getValue())) {
                    sufficientResource = Optional.of(mesRes);
                    // do NOT break: ensure filteredResources is fully populated
                } else {
                    filteredResources.add(mesRes);
                }
            }
        }

        if (filteredResources.size() == 0) {
            unreservedAtomicPool.remove(resReq.getName());
        } else {
            unreservedAtomicPool.put(resReq.getName(), filteredResources);
        }

        if (!sufficientResource.isPresent()) {
            logger.warn("No sufficient atomic resources found for resource requirement: {}",
                    TextFormat.shortDebugString(resReq.getResource()));
        }

        return sufficientResource;
    }

    private Optional<MesosResource> consumeUnreservedMerged(ResourceRequirement resReq) {
        Value desiredValue = resReq.getValue();
        Value availableValue = unreservedMergedPool.get(resReq.getName());

        if (sufficientValue(desiredValue, availableValue)) {
            unreservedMergedPool.put(resReq.getName(), ValueUtils.subtract(availableValue, desiredValue));
            Resource resource = ResourceUtils.getUnreservedResource(resReq.getName(), desiredValue);
            return Optional.of(new MesosResource(resource));
        } else {
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
        Collection<MesosResource> mesRsrcs = new ArrayList<MesosResource>();

        for (Resource resource : offer.getResourcesList()) {
            mesRsrcs.add(new MesosResource(resource));
        }

        return mesRsrcs;
    }

    private static Map<String, MesosResource> getReservedPool(
            Collection<MesosResource> mesosResources) {
        Map<String, MesosResource> reservedPool = new HashMap<String, MesosResource>();

        for (MesosResource mesResource : mesosResources) {
            if (mesResource.hasResourceId()) {
                reservedPool.put(mesResource.getResourceId(), mesResource);
            }
        }

        return reservedPool;
    }

    private static Map<String, List<MesosResource>> getUnreservedAtomicPool(
            Collection<MesosResource> mesosResources) {
        Map<String, List<MesosResource>> pool = new HashMap<String, List<MesosResource>>();

        for (MesosResource mesResource : getUnreservedAtomicResources(mesosResources)) {
            String name = mesResource.getName();
            List<MesosResource> resList = pool.get(name);

            if (resList == null) {
                resList = new ArrayList<MesosResource>();
            }

            resList.add(mesResource);
            pool.put(name, resList);
        }

        return pool;
    }

    private static Map<String, Value> getUnreservedMergedPool(
            Collection<MesosResource> mesosResources) {
        Map<String, Value> pool = new HashMap<String, Value>();

        for (MesosResource mesResource : getUnreservedMergedResources(mesosResources)) {
            String name = mesResource.getName();
            Value currValue = pool.get(name);

            if (currValue == null) {
                currValue = ValueUtils.getZero(mesResource.getType());
            }

            pool.put(name, ValueUtils.add(currValue, mesResource.getValue()));
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
            Collection<MesosResource> mesResources) {
        Collection<MesosResource> unreservedResources = new ArrayList<MesosResource>();

        for (MesosResource mesResource : mesResources) {
            if (!mesResource.hasResourceId()) {
                unreservedResources.add(mesResource);
            }
        }

        return unreservedResources;
    }

    private static Collection<MesosResource> getAtomicResources(
            Collection<MesosResource> mesosResources) {
        Collection<MesosResource> atomicResources = new ArrayList<>();

        for (MesosResource mesResource : mesosResources) {
            if (mesResource.isAtomic()) {
                atomicResources.add(mesResource);
            }
        }

        return atomicResources;
    }

    private static Collection<MesosResource> getMergedResources(
            Collection<MesosResource> mesosResources) {
        Collection<MesosResource> mergedResources = new ArrayList<>();

        for (MesosResource mesResource : mesosResources) {
            if (!mesResource.isAtomic()) {
                mergedResources.add(mesResource);
            }
        }

        return mergedResources;
    }
}
