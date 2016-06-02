package org.apache.mesos.agent

import spock.lang.Specification

/**
 *
 */
class AgentContextSpec extends Specification {

  def "parsing ports"() {

    AgentContext context = new AgentContext()
    def env = [:]
    context.env = env

    when:
    env.PORTS = "1234"

    then:
    context.getInternalPortList() == [1234]

    when:
    env.PORTS = "1234,4321"

    then:
    context.getInternalPortList() == [1234, 4321]

    when:
    env.PORTS = null

    then:
    context.getInternalPortList() == []
  }
}
