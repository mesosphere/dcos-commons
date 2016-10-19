package org.apache.mesos.util

import org.apache.mesos.Protos.Value.Range
import spock.lang.Specification

/**
 * Test Algorithm implementations.
 */
class AlgorithmsSpec extends Specification {

  def "merging overlapping ranges"() {
    given:
    List<Range> r1 = Arrays.asList(getRange(1, 3))
    List<Range> r2 = Arrays.asList(getRange(2, 4))

    when:
    def mergedRanges = Algorithms.mergeRanges(r1, r2)

    then:
    mergedRanges.size() == 1
    mergedRanges.get(0).getBegin() == 1
    mergedRanges.get(0).getEnd() == 4
  }


  def "merging non-overlapping ranges"() {
    given:
    List<Range> r1 = Arrays.asList(getRange(1, 3))
    List<Range> r2 = Arrays.asList(getRange(5, 7))

    when:
    def mergedRanges = Algorithms.mergeRanges(r1, r2)

    then:
    mergedRanges.size() == 2
    mergedRanges.get(0).getBegin() == 1
    mergedRanges.get(0).getEnd() == 3
    mergedRanges.get(1).getBegin() == 5
    mergedRanges.get(1).getEnd() == 7
  }

  def "merging internally overlapping ranges"() {
    given:
    List<Range> r1 = Arrays.asList(getRange(1, 5))
    List<Range> r2 = Arrays.asList(getRange(2, 4))

    when:
    def mergedRanges = Algorithms.mergeRanges(r1, r2)

    then:
    mergedRanges.size() == 1
    mergedRanges.get(0).getBegin() == 1
    mergedRanges.get(0).getEnd() == 5
  }

  def "subtract ranges"() {
    given:
    List<Range> r1 = Arrays.asList(getRange(1, 3), getRange(5, 7))
    List<Range> r2 = Arrays.asList(getRange(1, 3))

    when:
    def difference = Algorithms.subtractRanges(r1, r2)

    then:
    difference.size() == 1
    difference.get(0).getBegin() == 5
    difference.get(0).getEnd() == 7

  }

  def "subtract ranges for nil result"() {
    given:
    List<Range> r1 = Arrays.asList(getRange(2, 3))
    List<Range> r2 = Arrays.asList(getRange(1, 5))

    when:
    def difference = Algorithms.subtractRanges(r1, r2)

    then:
    difference.size() == 0
  }

  def getRange(int begin, int end) {
    Range.Builder builder = Range.newBuilder()
    builder.setBegin(begin)
    builder.setEnd(end)
    return builder.build()
  }
}
