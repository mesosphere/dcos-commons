package org.apache.mesos.offer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Label;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.state.StateStore;

/**
 * The Resource Cleaner provides recommended operations for cleaning up unexpected Reserved resources
 * and persistent volumes.
 */
public class ResourceCleaner {
  private Collection<String> expectedResourceIds;
  private Collection<String> expectedPersistenceIds;

  public ResourceCleaner(Collection<Resource> resources) {
    initializeIds(resources);
  }

  public ResourceCleaner(StateStore stateStore) {
    Collection<Resource> resources = new ArrayList<>();
    for (String execName : stateStore.fetchExecutorNames()) {
      for (Protos.TaskInfo taskInfo : stateStore.fetchTasks(execName)) {
        resources.addAll(taskInfo.getResourcesList());
        if (taskInfo.hasExecutor()) {
          resources.addAll(taskInfo.getExecutor().getResourcesList());
        }
      }
    }

    initializeIds(resources);
  }

  private void initializeIds(Collection<Resource> resources) {
    expectedResourceIds = getExpectedResourceIds(resources);
    expectedPersistenceIds = getPersistenceIds(resources);
  }

  private Collection<String> getPersistenceIds(Collection<Resource> resources) {
    List<String> persistenceIds = new ArrayList<>();

    for (Resource resource : resources) {
      String persistenceId = ResourceUtils.getPersistenceId(resource);
      if (persistenceId != null) {
        persistenceIds.add(persistenceId);
      }
    }

    return persistenceIds;
  }

  private Collection<String> getExpectedResourceIds(Collection<Resource> resources) {
    List<String> resourceIds = new ArrayList<>();

    for (Resource resource : resources) {
      String resourceId = ResourceUtils.getResourceId(resource);
      if (resourceId != null) {
        resourceIds.add(resourceId);
      }
    }

    return resourceIds;
  }

  public List<OfferRecommendation> evaluate(List<Offer> offers) {
    List<OfferRecommendation> unreserveRecommendations = new ArrayList<OfferRecommendation>();
    List<OfferRecommendation> destroyRecommendations = new ArrayList<OfferRecommendation>();

    for (Offer offer : offers) {
      destroyRecommendations.addAll(getDestroyRecommendations(offer, getPersistentVolumes(offer)));
      unreserveRecommendations.addAll(getUnreserveRecommendations(offer, getReservedResources(offer)));
    }

    List<OfferRecommendation> recommendations = new ArrayList<OfferRecommendation>();
    recommendations.addAll(destroyRecommendations);
    recommendations.addAll(unreserveRecommendations);

    return recommendations;
  }

  private List<OfferRecommendation> getDestroyRecommendations(Offer offer, Map<String, Resource> persistentVolumes) {
    if (expectedPersistenceIds == null) {
      return Collections.emptyList();
    }

    List<OfferRecommendation> recommendations = new ArrayList<OfferRecommendation>();

    for (Map.Entry<String, Resource> entry : persistentVolumes.entrySet()) {
      String persistenceId = entry.getKey();
      Resource resource = entry.getValue();

      if (!expectedPersistenceIds.contains(persistenceId)) {
        recommendations.add(new DestroyOfferRecommendation(offer, resource));
      }
    }

    return recommendations;
  }

  private List<OfferRecommendation> getUnreserveRecommendations(Offer offer, Map<String, Resource> reservedResources) {
    if (expectedResourceIds == null) {
      return Collections.emptyList();
    }

    List<OfferRecommendation> recommendations = new ArrayList<OfferRecommendation>();

    for (Map.Entry<String, Resource> entry : reservedResources.entrySet()) {
      String resourceId = entry.getKey();
      Resource resource = entry.getValue();

      if (!expectedResourceIds.contains(resourceId)) {
        recommendations.add(new UnreserveOfferRecommendation(offer, resource));
      }
    }

    return recommendations;
  }

  private Map<String, Resource> getReservedResources(Offer offer) {
    Map<String, Resource> reservedResources = new HashMap<String, Resource>();

    for (Resource resource : offer.getResourcesList()) {
      if (resource.hasReservation()) {
        String resourceId = getResourceId(resource);
        if (resourceId != null) {
          reservedResources.put(resourceId, resource);
        }
      }
    }

    return reservedResources;
  }

  private String getResourceId(Resource resource) {
    for (Label label : resource.getReservation().getLabels().getLabelsList()) {
      if (label.getKey().equals(ResourceRequirement.RESOURCE_ID_KEY)) {
        return label.getValue();
      }
    }

    return null;
  }

  private Map<String, Resource> getPersistentVolumes(Offer offer) {
    Map<String, Resource> volumes = new HashMap<String, Resource>();

    for (Resource resource : offer.getResourcesList()) {
      if (resource.hasDisk()) {
        String persistenceId = getPersistenceId(resource);
        if (persistenceId != null) {
          volumes.put(persistenceId, resource);
        }
      }
    }

    return volumes;
  }

  private String getPersistenceId(Resource resource) {
    if (!resource.getDisk().hasPersistence()) {
      return null;
    } else {
      return resource.getDisk().getPersistence().getId();
    }
  }
}
