package org.apache.mesos.collections

import spock.lang.Specification

/**
 *
 */
class MapUtilSpec extends Specification {

  def "filtering maps"() {
    given:
    def map = ["MESOS_BLAH": "MESOS_BLAH", "MESOS_BLAH2": "MESOS_BLAH2", "RED_LEADER1": "Tsui Choi "]
    def props = new Properties()
    props.putAll(map)

    expect:
    map.size() == 3
    MapUtil.propertyMapFilter(props, new StartsWithPredicate("MESOS")).size() == 2
    MapUtil.propertyMapFilter(props, new StartsWithPredicate("RED")).size() == 1
  }
}
