package com.mesosphere.sdk.offer;

import org.apache.mesos.Protos;

public interface UninstallRecommendation extends OfferRecommendation {
    Protos.Resource getResource();
}
