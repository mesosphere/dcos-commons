package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos;

/**
 * This interface encapsulates a Mesos resource which is to be unreserved or destroyed.
 */
public interface UninstallRecommendation extends OfferRecommendation {
    /**
     * Returns the Mesos resource to be unreserved or destroyed.
     */
    Protos.Resource getResource();
}
