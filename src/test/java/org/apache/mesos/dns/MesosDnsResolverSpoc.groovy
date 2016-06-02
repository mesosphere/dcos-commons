package org.apache.mesos.dns

import spock.lang.Specification

/**
 *
 */
class MesosDnsResolverSpoc extends Specification {

  def "provides a valid fully qualified name for task"() {

    given:
    def resolver = new MesosDnsResolver(framework, domain)

    expect:
    host == resolver.getFullNameForTask(task)

    where:
    framework   | domain  | task   | host
    "framework" | "mesos" | "task" | "task.framework.mesos"
    "framework" | ""      | "task" | "task.framework"
    "framework" | null    | "task" | "task.framework"
  }

  def "provides a valid fully qualified SRV name for task"() {

    given:
    def resolver = new MesosDnsResolver(framework, domain)

    expect:
    host == resolver.getFullSRVNameForTask(task)

    where:
    framework   | domain  | task   | host
    "framework" | "mesos" | "task" | "task.framework.mesos."
    "framework" | ""      | "task" | "task.framework."
    "framework" | null    | "task" | "task.framework."
  }
}
