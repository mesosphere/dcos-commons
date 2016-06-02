package org.apache.mesos.collections;

import com.google.common.base.Predicate;

/**
 * A Predicate for filtering a collection based on what a string starts with.
 */
public class StartsWithPredicate implements Predicate<String> {

  private String startWithString;

  public StartsWithPredicate(String startWithString) {
    this.startWithString = startWithString;
  }

  @Override
  public boolean apply(String s) {
    return s.startsWith(startWithString);
  }
}
