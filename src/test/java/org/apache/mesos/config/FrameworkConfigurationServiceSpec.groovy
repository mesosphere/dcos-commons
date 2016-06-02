package org.apache.mesos.config

import spock.lang.Specification

class FrameworkConfigurationServiceSpec extends Specification {

  def "add a property"() {
    ConfigurationService service = new FrameworkConfigurationService()
    service.setValue(property, value)

    expect:
    value == service.get(property)

    where:
    property   | value
    "property" | "value"
    null       | ""
  }

  def "working with namespaces"() {
    ConfigurationService service = new FrameworkConfigurationService()

    expect:
    service.getNamespaces().size() == 1

    when:
    service.setValue("ns", "property", "value")

    then:
    service.getNamespaces().size() == 2

    when:
    service.setValue("ns", "property2", "value")

    then:
    service.getNamespaces().size() == 2
  }
}
