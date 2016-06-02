package org.apache.mesos.config

import spock.lang.Specification

/**
 *
 */
class ConfigurationChangeDetectorSpec extends Specification {

  public static final String CONFIG_ROOT_NAMESPACE = "*";
  public static final String CONFIG_UPDATABLE_NAMESPACE = "updatable.ns";

  def newConfig = [:]
  def oldConfig = [:]

  def setup() {
    getDefaultConfigList()

    newConfig.put(CONFIG_ROOT_NAMESPACE, getConfigMap(getDefaultConfigList()))
    oldConfig.put(CONFIG_ROOT_NAMESPACE, getConfigMap(getDefaultConfigList()))

  }

  private List<ConfigProperty> getDefaultConfigList() {
    def configs = []
    configs << createProperty("test", "again")
    configs << createProperty("name1", "value1")
    configs << createProperty("fw", "hdfs")
    configs << createProperty("port0", "8000")
    return configs
  }

  def "same configs are equal"() {
    def changeDetector = new ConfigurationChangeDetector(newConfig, oldConfig,
        new ConfigurationChangeNamespaces(CONFIG_ROOT_NAMESPACE, CONFIG_UPDATABLE_NAMESPACE))

    expect:
    !changeDetector.isChangeDetected()
  }

  def Map getConfigMap(properties) {
    def configMap = [:]
    properties.each {
      configMap.put(it.name, it)
    }
    return configMap
  }

  def ConfigProperty createProperty(name, value) {
    new ConfigProperty(name, value)
  }

  def "config size changed with few configs"() {

    def configs = getDefaultConfigList()
    configs << createProperty("new", "config")
    oldConfig.put(CONFIG_ROOT_NAMESPACE, getConfigMap(configs))
    def changeDetector = new ConfigurationChangeDetector(newConfig, oldConfig,
        new ConfigurationChangeNamespaces(CONFIG_ROOT_NAMESPACE, CONFIG_UPDATABLE_NAMESPACE))

    expect:
    changeDetector.isChangeDetected()
    changeDetector.getMissingConfigs().size() == 1

  }

  def "config size changed with more configs"() {
    oldConfig.get(CONFIG_ROOT_NAMESPACE).remove("fw")
    def changeDetector = new ConfigurationChangeDetector(newConfig, oldConfig,
        new ConfigurationChangeNamespaces(CONFIG_ROOT_NAMESPACE, CONFIG_UPDATABLE_NAMESPACE))

    expect:
    changeDetector.isChangeDetected()
    changeDetector.getExtraConfigs().size() == 1

  }

  def "updatable config changed"() {
    def configs = getDefaultConfigList()
    def changeable = configs.first()
    changeable.value = "changed"
    newConfig.put(CONFIG_ROOT_NAMESPACE, getConfigMap(configs))
    oldConfig.put(CONFIG_UPDATABLE_NAMESPACE, getConfigMap(changeable))  // the older config must have knowledge of what is changeable
    def changeDetector = new ConfigurationChangeDetector(newConfig, oldConfig,
        new ConfigurationChangeNamespaces(CONFIG_ROOT_NAMESPACE, CONFIG_UPDATABLE_NAMESPACE))

    expect:
    changeDetector.isChangeDetected()
    changeDetector.getChangedProperties().size() == 1
    changeDetector.getValidChangedProperties().size() == 1
    with(changeDetector.getValidChangedProperties().first()) {
      name == "test"
      oldValue == "again"
      newValue == "changed"
    }
  }

  def "updatable configs"() {
    def configs = getDefaultConfigList()
    def changeable = configs.first()
    changeable.value = "changed"
    oldConfig.put(CONFIG_ROOT_NAMESPACE, getConfigMap(configs))
    oldConfig.put(CONFIG_UPDATABLE_NAMESPACE, getConfigMap(changeable))
    def changeDetector = new ConfigurationChangeDetector(newConfig, oldConfig,
        new ConfigurationChangeNamespaces(CONFIG_ROOT_NAMESPACE, CONFIG_UPDATABLE_NAMESPACE))

    expect:
    changeDetector.getUpdatableProperties().size() == 1
  }

  def "non-updatable config changed"() {
    def configs = getDefaultConfigList()
    def changeable = configs.first()
    changeable.value = "changed"
    oldConfig.put(CONFIG_ROOT_NAMESPACE, getConfigMap(configs))
    def changeDetector = new ConfigurationChangeDetector(newConfig, oldConfig,
        new ConfigurationChangeNamespaces(CONFIG_ROOT_NAMESPACE, CONFIG_UPDATABLE_NAMESPACE))

    expect:
    changeDetector.getUpdatableProperties().size() == 0
    changeDetector.getChangeViolationProperties().size() == 1
  }

  def "list of config changes"() {
    def configs = getDefaultConfigList()
    configs << createProperty("test", "foo")
    newConfig.put(CONFIG_ROOT_NAMESPACE, getConfigMap(configs))
    def changeDetector = new ConfigurationChangeDetector(newConfig, oldConfig,
        new ConfigurationChangeNamespaces(CONFIG_ROOT_NAMESPACE, CONFIG_UPDATABLE_NAMESPACE))

    expect:
    changeDetector.isChangeDetected()
    changeDetector.getChangedProperties().size() == 1
    changeDetector.getChangedProperties().first() == new ChangedProperty("test", "again", "foo")
  }

  def "port changes are not changes"() {

    def configs = getDefaultConfigList()
    configs << createProperty("port0", "9000")
    oldConfig.put(CONFIG_ROOT_NAMESPACE, getConfigMap(configs))
    def changeDetector = new ConfigurationChangeDetector(newConfig, oldConfig,
        new ConfigurationChangeNamespaces(CONFIG_ROOT_NAMESPACE, CONFIG_UPDATABLE_NAMESPACE))

    expect:
    !changeDetector.isChangeDetected()
    changeDetector.getMissingConfigs().size() == 0

  }
}
