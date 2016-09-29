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
 * A representation of the pool of resources available in a single Offer.
 **/
public class MesosResourcePool {
  private static final Logger logger = LoggerFactory.getLogger(MesosResourcePool.class);

  private Offer offer;
  private Collection<MesosResource> mesosResources;
  private Map<String, List<MesosResource>> unreservedAtomicPool;
  private Map<String, Value> unreservedMergedPool;
  private Map<String, MesosResource> reservedPool;

  public MesosResourcePool(Offer offer) {
    this.offer = offer;
    this.mesosResources = getMesosResourcesInternal();
    this.unreservedAtomicPool = getUnreservedAtomicPool(offer);
    this.unreservedMergedPool = getUnreservedMergedPool(offer);
    this.reservedPool = getReservedPool(offer);
  }

  public Offer getOffer() {
    return offer;
  }

  public Map<String, List<MesosResource>> getUnreservedAtomicPool() {
    return unreservedAtomicPool;
  }

  public Map<String, Value> getUnreservedMergedPool() {
    return unreservedMergedPool;
  }

  public Map<String, MesosResource> getReservedPool() {
    return reservedPool;
  }

  public MesosResource consume(ResourceRequirement resReq) {
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
    return null;
  }

  public MesosResource consume(DynamicPortRequirement dynamicPortRequirement) {
    Value availableValue = unreservedMergedPool.get(dynamicPortRequirement.getName());

    if (availableValue == null) {
      return null;
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

    return null;
  }

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

  private MesosResource consumeReserved(ResourceRequirement resReq) {
    MesosResource mesRes = reservedPool.get(resReq.getResourceId());

    if (mesRes != null) {
      if (mesRes.isAtomic()) {
        if (sufficientValue(resReq.getValue(), mesRes.getValue())) {
          reservedPool.remove(resReq.getResourceId());
        } else {
          return null;
        }
      } else {
        reservedPool.remove(resReq.getResourceId());
      }
    }

    return mesRes;
  }

  private MesosResource consumeAtomic(ResourceRequirement resReq) {
    Value desiredValue = resReq.getValue();
    List<MesosResource> atomicResources = unreservedAtomicPool.get(resReq.getName());
    List<MesosResource> filteredResources = new ArrayList<>();
    MesosResource sufficientResource = null;

    if (atomicResources != null) {
      for (MesosResource mesRes : atomicResources) {
        if (sufficientValue(desiredValue, mesRes.getValue())) {
          sufficientResource = mesRes;
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

    if (sufficientResource == null) {
      logger.warn("No sufficient atomic resources found for resource requirement: {}",
          TextFormat.shortDebugString(resReq.getResource()));
    }

    return sufficientResource;
  }

  private MesosResource consumeUnreservedMerged(ResourceRequirement resReq) {
    Value desiredValue = resReq.getValue();
    Value availableValue = unreservedMergedPool.get(resReq.getName());

    if (sufficientValue(desiredValue, availableValue)) {
      unreservedMergedPool.put(resReq.getName(), ValueUtils.subtract(availableValue, desiredValue));
      Resource resource = ResourceUtils.getUnreservedResource(resReq.getName(), desiredValue);
      return new MesosResource(resource);
    } else {
      return null;
    }
  }

  private boolean sufficientValue(Value desired, Value available) {
    if (desired == null) {
      return true;
    } else if (available == null) {
      return false;
    }

    Value difference = ValueUtils.subtract(desired, available);
    return ValueUtils.compare(difference, ValueUtils.getZero(desired.getType())) <= 0;
  }

  private Collection<MesosResource> getMesosResourcesInternal() {
    Collection<MesosResource> mesRsrcs = new ArrayList<MesosResource>();

    for (Resource resource : offer.getResourcesList()) {
      mesRsrcs.add(new MesosResource(resource));
    }

    return mesRsrcs;
  }

  private Map<String, MesosResource> getReservedPool(Offer offer) {
    Map<String, MesosResource> reservedPool = new HashMap<String, MesosResource>();

    for (MesosResource mesResource : mesosResources) {
      if (mesResource.hasResourceId()) {
        reservedPool.put(mesResource.getResourceId(), mesResource);
      }
    }

    return reservedPool;
  }

  private Map<String, List<MesosResource>> getUnreservedAtomicPool(Offer offer) {
    Map<String, List<MesosResource>> pool = new HashMap<String, List<MesosResource>>();

    for (MesosResource mesResource : getUnreservedAtomicResources()) {
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

  private Map<String, Value> getUnreservedMergedPool(Offer offer) {
    Map<String, Value> pool = new HashMap<String, Value>();

    for (MesosResource mesResource : getUnreservedMergedResources()) {
      String name = mesResource.getName();
      Value currValue = pool.get(name);

      if (currValue == null) {
        currValue = ValueUtils.getZero(mesResource.getType());
      }

      pool.put(name, ValueUtils.add(currValue, mesResource.getValue()));
    }

    return pool;
  }

  private Collection<MesosResource> getAtomicResources() {
    Collection<MesosResource> atomicResources = new ArrayList<MesosResource>();

    for (MesosResource mesResource : mesosResources) {
      if (mesResource.isAtomic()) {
        atomicResources.add(mesResource);
      }
    }

    return atomicResources;
  }

  private Collection<MesosResource> getMergedResources() {
    Collection<MesosResource> mergedResources = new ArrayList<MesosResource>();

    for (MesosResource mesResource : mesosResources) {
      if (!mesResource.isAtomic()) {
        mergedResources.add(mesResource);
      }
    }

    return mergedResources;
  }

  private Collection<MesosResource> getUnreservedAtomicResources() {
    return getUnreservedResources(getAtomicResources());
  }

  private Collection<MesosResource> getUnreservedMergedResources() {
    return getUnreservedResources(getMergedResources());
  }

  private Collection<MesosResource> getUnreservedResources(Collection<MesosResource> mesResources) {
    Collection<MesosResource> unreservedResources = new ArrayList<MesosResource>();

    for (MesosResource mesResource : mesResources) {
      if (!mesResource.hasResourceId()) {
        unreservedResources.add(mesResource);
      }
    }

    return unreservedResources;
  }
}
