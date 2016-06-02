package org.apache.mesos.collections

import com.google.common.collect.Maps
import spock.lang.Specification

/**
 *
 */
class StartsWithPredicateSpec extends Specification {

  def "predicate filter"() {
    given:
    def map = ["MESOS_BLAH": "MESOS_BLAH", "MESOS_BLAH2": "MESOS_BLAH2", "RED_LEADER1": "Tsui Choi "]

    expect:
    map.size() == 3
    Maps.filterKeys(map, new StartsWithPredicate("MESOS")).size() == 2
    Maps.filterKeys(map, new StartsWithPredicate("RED")).size() == 1

  }
}
