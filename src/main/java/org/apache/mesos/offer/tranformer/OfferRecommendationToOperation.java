package org.apache.mesos.offer.tranformer;

import com.google.common.base.Function;
import org.apache.mesos.Protos;
import org.apache.mesos.offer.OfferRecommendation;

/**
 * Using guava Collections2 to transform OfferRecommentations into Operations.
 * Collections2.transform().
 */
public class OfferRecommendationToOperation
  implements Function<OfferRecommendation, Protos.Offer.Operation> {

  @Override
  public Protos.Offer.Operation apply(OfferRecommendation offerRecommendation) {
    return offerRecommendation.getOperation();
  }

}
