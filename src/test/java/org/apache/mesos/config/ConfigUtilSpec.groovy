package org.apache.mesos.config

import spock.lang.Specification

class ConfigUtilSpec extends Specification {

  def "convert env to property format"() {

    expect:
    value == ConfigUtil.fromEnvVarNameToPropertyName(env)

    where:
    env                   | value
    "TAKE_THAT"           | "take.that"
    ""                    | ""
    null                  | ""
    "BLUE__DASH__MONKEYS" | "blue-monkeys"
  }

  def "convert property to env format"() {

    expect:
    env == ConfigUtil.fromPropertyNameToEnvVarName(property)

    where:
    property       | env
    "take.that"    | "TAKE_THAT"
    "blue-monkeys" | "BLUE__DASH__MONKEYS"
  }

  def "check blank values parse as empty optional"() {
    expect:
    !ConfigUtil.parseConfigValue(strValue, type).isPresent()

    where:
    strValue | type
    null     | Boolean.class
    null     | Integer.class
    null     | Long.class
    null     | String.class
    null     | Double.class
    null     | Float.class
    ""       | Boolean.class
    ""       | Integer.class
    ""       | Long.class
    ""       | String.class
    ""       | Double.class
    ""       | Float.class
    "   "    | Boolean.class
    "   "    | Integer.class
    "   "    | Long.class
    "   "    | String.class
    "   "    | Double.class
    "   "    | Float.class
  }

  def "check populated values parse as correct values"() {
    expect:
    conversion == ConfigUtil.parseConfigValue(strValue, type).get()

    where:
    strValue | type          | conversion
    "true"   | Boolean.class | true
    "20"     | Integer.class | 20
    "20"     | Long.class    | 20L
    "20"     | String.class  | "20"
    "20.2"   | Double.class  | 20.2d
    "20.2"   | Float.class   | 20.2d
  }

  def "check boolean parse failure results maps to false"() {
    expect:
    !ConfigUtil.parseConfigValue(strValue, type).get()

    where:
    strValue | type
    "hello"  | Boolean.class
  }

  def "check number parse failure throws exception"() {
    when:
    ConfigUtil.parseConfigValue(strValue, type)

    then:
    thrown(NumberFormatException)

    where:
    strValue | type
    "hello"  | Integer.class
    "hi"     | Long.class
    "hey"    | Double.class
    "string" | Float.class
  }

  def "check unset typed value returns default"() {
    def configService = Mock(ConfigurationService)
    configService.get(_) >> null

    expect:
    defaultValue == ConfigUtil.getTypedValue(configService, "property", defaultValue)

    where:
    defaultValue << [false, 10, 10L, "10", 10.1 as Double, 10.1 as Float]
  }

  def "check populated values converted and returned for typed values"() {
    def configService = Mock(ConfigurationService)
    configService.get(_) >> strValue

    expect:
    conversion == ConfigUtil.getTypedValue(configService, "property", defaultValue)

    where:
    strValue | defaultValue   | conversion
    "true"   | false          | true
    "20"     | 10             | 20
    "20"     | 10L            | 20L
    "20"     | "10"           | "20"
    "20.2"   | 10.1 as Double | 20.2d
    "20.2"   | 10.1 as Float  | 20.2d
  }
}
