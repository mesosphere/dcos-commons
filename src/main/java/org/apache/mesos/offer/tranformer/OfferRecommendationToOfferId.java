package org.apache.mesos.offer.tranformer;

import com.google.common.base.Function;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRecommendation;

/**
 * Using guava Collections2 to transform OfferRecommentations into OfferID.
 * Collections2.transform().
 */
public class OfferRecommendationToOfferId implements Function<OfferRecommendation, Protos.OfferID> {

  @Override
  public Protos.OfferID apply(OfferRecommendation offerRecommendation) {
    return offerRecommendation.getOffer().getId();
  }
}
