package org.apache.mesos.offer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.mesos.Protos.ExecutorInfo;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Resource.DiskInfo;
import org.apache.mesos.Protos.Resource.DiskInfo.Persistence;
import org.apache.mesos.Protos.Resource.ReservationInfo;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Value;

import org.apache.mesos.protobuf.ValueUtils;

import java.util.*;

/**
 * The OfferEvaluator processes Offers and produces OfferRecommendations.
 * The determination of what OfferRecommendations, if any should be made are made
 * in reference to the OfferRequirement with which it was constructed.  In the
 * case where an OfferRequirement has not been provided no OfferRecommendations
 * are ever returned.
 */
public class OfferEvaluator {
  private static final Log log = LogFactory.getLog(OfferEvaluator.class);

  private OfferRequirement requirement; //TODO(nick) remove state in favor of passing as an evaluate() parameter

  public OfferEvaluator() {
    this.requirement = null;
  }

  public OfferEvaluator(OfferRequirement requirement) {
    this.requirement = requirement;
  }

  public OfferRequirement getOfferRequirement() {
    return requirement;
  }

  public void setOfferRequirement(OfferRequirement requirement) {
    this.requirement = requirement;
  }

  public List<OfferRecommendation> evaluate(List<Offer> offers) {

    for (Offer offer : offers) {
      List<OfferRecommendation> recommendations = evaluate(offer);
      if (recommendations != null) {
        return recommendations;
      }
    }

    return Collections.emptyList();
  }

  public List<OfferRecommendation> evaluate(Offer offer) {
    MesosResourcePool pool = new MesosResourcePool(offer);

    List<OfferRecommendation> unreserves = new ArrayList<OfferRecommendation>();
    List<OfferRecommendation> reserves = new ArrayList<OfferRecommendation>();
    List<OfferRecommendation> creates = new ArrayList<OfferRecommendation>();
    List<OfferRecommendation> launches = new ArrayList<OfferRecommendation>();

    ExecutorRequirement execReq = requirement.getExecutorRequirement();
    FulfilledRequirement fulfilledExecutorRequirement = null;
    if (execReq != null && execReq.desiresResources()) {
      fulfilledExecutorRequirement = FulfilledRequirement.fulfillRequirement(
          execReq.getResourceRequirements(),
          offer,
          pool);

      if (fulfilledExecutorRequirement == null) {
        return Collections.emptyList();
      }

      unreserves.addAll(fulfilledExecutorRequirement.getUnreserveRecommendations());
      reserves.addAll(fulfilledExecutorRequirement.getReserveRecommendations());
      creates.addAll(fulfilledExecutorRequirement.getCreateRecommendations());
    }

    for (TaskRequirement taskReq : requirement.getTaskRequirements()) {
      FulfilledRequirement fulfilledTaskRequirement =
        FulfilledRequirement.fulfillRequirement(taskReq.getResourceRequirements(), offer, pool);

      if (fulfilledTaskRequirement == null) {
        return Collections.emptyList();
      }

      unreserves.addAll(fulfilledTaskRequirement.getUnreserveRecommendations());
      reserves.addAll(fulfilledTaskRequirement.getReserveRecommendations());
      creates.addAll(fulfilledTaskRequirement.getCreateRecommendations());
      launches.add(
        new LaunchOfferRecommendation(
          offer,
          getFulfilledTaskInfo(
            taskReq,
            fulfilledTaskRequirement,
            execReq,
            fulfilledExecutorRequirement)));
    }

    List<OfferRecommendation> recommendations = new ArrayList<OfferRecommendation>();
    recommendations.addAll(unreserves);
    recommendations.addAll(reserves);
    recommendations.addAll(creates);
    recommendations.addAll(launches);

    return recommendations;
  }

  private static class FulfilledRequirement {
    private List<Resource> fulfilledResources = new ArrayList<Resource>();
    private List<OfferRecommendation> unreserveRecommendations = new ArrayList<OfferRecommendation>();
    private List<OfferRecommendation> reserveRecommendations = new ArrayList<OfferRecommendation>();
    private List<OfferRecommendation> createRecommendations = new ArrayList<OfferRecommendation>();

    private FulfilledRequirement(
        List<Resource> fulfilledResources,
        List<OfferRecommendation> unreserveRecommendations,
        List<OfferRecommendation> reserveRecommendations,
        List<OfferRecommendation> createRecommendations) {

      this.fulfilledResources = fulfilledResources;
      this.unreserveRecommendations = unreserveRecommendations;
      this.reserveRecommendations = reserveRecommendations;
      this.createRecommendations = createRecommendations;
    }

    public static FulfilledRequirement fulfillRequirement(
        Collection<ResourceRequirement> resourceRequirements,
        Offer offer,
        MesosResourcePool pool) {

      List<Resource> fulfilledResources = new ArrayList<Resource>();
      List<OfferRecommendation> unreserveRecommendations = new ArrayList<OfferRecommendation>();
      List<OfferRecommendation> reserveRecommendations = new ArrayList<OfferRecommendation>();
      List<OfferRecommendation> createRecommendations = new ArrayList<OfferRecommendation>();

      for (ResourceRequirement resReq : resourceRequirements) {
        MesosResource mesRes = pool.consume(resReq);
        if (mesRes == null) {
          log.warn("Failed to satisfy resource requirement: " + resReq.getResource());
          return null;
        } else {
          log.info("Satisfying resource requirement: " +
              resReq.getResource() +
              "with resource: " +
              mesRes.getResource());
        }

        Resource fulfilledResource = getFulfilledResource(resReq, mesRes);
        if (resReq.expectsResource()) {
          log.info("Expects Resource");
          // Compute any needed resource pool consumption / release operations
          // as well as any additional needed Mesos Operations
          if (expectedValueChanged(resReq, mesRes)) {
            Value reserveValue = ValueUtils.subtract(resReq.getValue(), mesRes.getValue());
            Value unreserveValue = ValueUtils.subtract(mesRes.getValue(), resReq.getValue());

            if (ValueUtils.compare(unreserveValue, ValueUtils.getZero(unreserveValue.getType())) > 0) {
              log.info("Updates reserved resource with less reservation");
              Resource unreserveResource = ResourceUtils.getDesiredResource(
                  resReq.getRole(),
                  resReq.getPrincipal(),
                  resReq.getName(),
                  unreserveValue);
              unreserveResource = ResourceUtils.setResourceId(unreserveResource, resReq.getResourceId());

              pool.release(new MesosResource(ResourceUtils.getRawResource(resReq.getName(), unreserveValue)));
              unreserveRecommendations.add(new UnreserveOfferRecommendation(offer, unreserveResource));
              fulfilledResource = getFulfilledResource(resReq, new MesosResource(resReq.getResource()));
            }

            if (ValueUtils.compare(reserveValue, ValueUtils.getZero(reserveValue.getType())) > 0) {
              log.info("Updates reserved resource with additional reservation");
              Resource reserveResource = ResourceUtils.getDesiredResource(
                  resReq.getRole(),
                  resReq.getPrincipal(),
                  resReq.getName(),
                  reserveValue);

              if (pool.consume(new ResourceRequirement(reserveResource)) != null) {
                reserveResource = ResourceUtils.setResourceId(reserveResource, resReq.getResourceId());
                reserveRecommendations.add(new ReserveOfferRecommendation(offer, reserveResource));
                fulfilledResource = getFulfilledResource(resReq, new MesosResource(resReq.getResource()));
              } else {
                log.warn("Insufficient resources to increase resource usage.");
                return null;
              }
            }
          }
        } else {
          if (resReq.reservesResource()) {
            log.info("Reserves Resource");
            reserveRecommendations.add(new ReserveOfferRecommendation(offer, fulfilledResource));
          }

          if (resReq.createsVolume()) {
            log.info("Creates Volume");
            createRecommendations.add(new CreateOfferRecommendation(offer, fulfilledResource));
          }
        }

        log.info("Fulfilled resource: " + fulfilledResource);
        fulfilledResources.add(fulfilledResource);
      }

      return new FulfilledRequirement(
          fulfilledResources,
          unreserveRecommendations,
          reserveRecommendations,
          createRecommendations);
    }

    public List<Resource> getFulfilledResources() {
      return fulfilledResources;
    }

    public List<OfferRecommendation> getUnreserveRecommendations() {
      return unreserveRecommendations;
    }

    public List<OfferRecommendation> getReserveRecommendations() {
      return reserveRecommendations;
    }

    public List<OfferRecommendation> getCreateRecommendations() {
      return createRecommendations;
    }
  }


  private static boolean expectedValueChanged(ResourceRequirement resReq, MesosResource mesRes) {
    return !ValueUtils.equal(resReq.getValue(), mesRes.getValue());
  }

  private static Resource getFulfilledResource(ResourceRequirement resReq, MesosResource mesRes) {
    Resource.Builder builder = Resource.newBuilder(mesRes.getResource());
    builder.setRole(resReq.getResource().getRole());

    ReservationInfo resInfo = getFulfilledReservationInfo(resReq, mesRes);
    if (resInfo != null) {
      builder.setReservation(resInfo);
    }

    DiskInfo diskInfo = getFulfilledDiskInfo(resReq, mesRes);
    if (diskInfo != null) {
      builder.setDisk(diskInfo);
    }

    return builder.build();
  }

  private static ReservationInfo getFulfilledReservationInfo(ResourceRequirement resReq, MesosResource mesRes) {
    if (!resReq.reservesResource()) {
      return null;
    } else {
      ReservationInfo.Builder resBuilder = ReservationInfo.newBuilder(resReq.getResource().getReservation());
      resBuilder.setLabels(
          ResourceUtils.setResourceId(
            resReq.getResource().getReservation().getLabels(),
            UUID.randomUUID().toString()));
      return resBuilder.build();
    }
  }

  private static DiskInfo getFulfilledDiskInfo(ResourceRequirement resReq, MesosResource mesRes) {
    if (!resReq.getResource().hasDisk()) {
      return null;
    }

    DiskInfo.Builder builder = DiskInfo.newBuilder(resReq.getResource().getDisk());
    if (mesRes.getResource().getDisk().hasSource()) {
      builder.setSource(mesRes.getResource().getDisk().getSource());
    }

    Persistence persistence = getFulfilledPersistence(resReq);
    if (persistence != null) {
      builder.setPersistence(persistence);
    }

    return builder.build();
  }

  private static Persistence getFulfilledPersistence(ResourceRequirement resReq) {
    if (!resReq.createsVolume()) {
      return null;
    } else {
      String persistenceId = UUID.randomUUID().toString();
      return Persistence.newBuilder(resReq.getResource().getDisk().getPersistence()).setId(persistenceId).build();
    }
  }

  private TaskInfo getFulfilledTaskInfo(
      TaskRequirement taskReq,
      FulfilledRequirement fulfilledTaskRequirement,
      ExecutorRequirement execReq,
      FulfilledRequirement fulfilledExecutorRequirement) {

    TaskInfo taskInfo = taskReq.getTaskInfo();
    List<Resource> fulfilledTaskResources = fulfilledTaskRequirement.getFulfilledResources();
    TaskInfo.Builder taskBuilder =
      TaskInfo.newBuilder(taskInfo)
      .clearResources()
      .addAllResources(fulfilledTaskResources);

    if (execReq != null) {
      ExecutorInfo execInfo = execReq.getExecutorInfo();
      ExecutorInfo.Builder execBuilder =
              ExecutorInfo.newBuilder(execInfo)
                      .clearResources();

      if (fulfilledExecutorRequirement != null) {
        List<Resource> fulfilledExecutorResources = fulfilledExecutorRequirement.getFulfilledResources();
        execBuilder.addAllResources(fulfilledExecutorResources);
      } else {
        execBuilder.addAllResources(execInfo.getResourcesList());
      }

      taskBuilder.setExecutor(execBuilder.build());
    }

    return taskBuilder.build();
  }
}
