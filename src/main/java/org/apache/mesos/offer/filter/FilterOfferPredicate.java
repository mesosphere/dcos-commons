package org.apache.mesos.offer.filter;

import com.google.common.base.Predicate;
import org.apache.mesos.Protos;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Predicate to filter a list of Offers and remove Offers which are in
 * the filter set.  This is commonly used to filter accepted offers from
 * a list of offeres to get the unaccepted offers.
 */
public class FilterOfferPredicate implements Predicate<Protos.Offer> {

  private Set<Protos.OfferID> filteredOfferIdSet = new HashSet<>();

  public FilterOfferPredicate(List<Protos.Offer> filterList) {
    for (Protos.Offer offer : filterList) {
      filteredOfferIdSet.add(offer.getId());
    }
  }

  public FilterOfferPredicate(Collection<Protos.OfferID> filteredOfferIdSet) {
    this.filteredOfferIdSet.addAll(filteredOfferIdSet);
  }

  @Override
  public boolean apply(Protos.Offer offer) {
    return !filteredOfferIdSet.contains(offer.getId());
  }
}
