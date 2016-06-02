package org.apache.mesos.acme.offer;

import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRequirement;

/**
 */
public interface AcmeOfferRequirementProvider {

  OfferRequirement getNewOfferRequirement(String configName, int brokerId);

  OfferRequirement getReplacementOfferRequirement(Protos.TaskInfo taskInfo);

  OfferRequirement getUpdateOfferRequirement(String configName, Protos.TaskInfo info);

}
