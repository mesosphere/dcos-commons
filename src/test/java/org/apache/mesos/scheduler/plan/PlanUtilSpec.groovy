package org.apache.mesos.scheduler.plan

import org.apache.mesos.Protos
import org.apache.mesos.offer.OfferRequirement
import spock.lang.Specification

/**
 *
 */
class PlanUtilSpec extends Specification {

  def "works with null blocks"() {
    expect:
    StageUtil.getAllIncompleteBlocks(null) == []
  }

  class TestPhase implements Phase {

    def id
    def complete = false
    def blocks = []

    TestPhase(id, complete) {
      this.id = id
      this.complete = complete
    }

    TestPhase(id) {
      this(id, false)
    }


    @Override
    List<? extends Block> getBlocks() {
      return blocks
    }

    @Override
    Block getBlock(UUID id) {
      return null
    }

    @Override
    Block getBlock(int index) {
      return null
    }

    @Override
    boolean isComplete() {
      return complete
    }

    @Override
    UUID getId() {
      return id
    }

    @Override
    String getName() {
      return null
    }
  }

  class TestBlock implements Block {

    def name

    def id

    TestBlock(UUID id, name) {
      this.id = id
      this.name = name
    }

    @Override
    Status getStatus() {
      return null
    }

    @Override
    void setStatus(Status newStatus) {

    }

    @Override
    boolean isPending() {
      return false
    }

    @Override
    boolean isInProgress() {
      return false
    }

    @Override
    OfferRequirement start() {
      return null
    }

    @Override
    void update(Protos.TaskStatus status) {

    }

    @Override
    UUID getId() {
      return id
    }

    @Override
    String getMessage() {
      return null
    }

    @Override
    String getName() {
      return name
    }

    @Override
    boolean isComplete() {
      return false
    }
  }
}
