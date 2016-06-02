package org.apache.mesos.config

import com.fasterxml.jackson.core.type.TypeReference
import spock.lang.Specification

import java.nio.charset.Charset

/**
 *
 */
class ConfigJsonSerializerSpec extends Specification {
  def serializer = new ConfigJsonSerializer()

  def "serialize from config properties to json bytes"() {
    Map<String, Map<String, ConfigProperty>> nsMap = new HashMap<>()
    Map<String, ConfigProperty> propertyMap = new HashMap<>()
    def config = new ConfigProperty("fav-animal", "unicorn")
    propertyMap.put(config.name, config)

    nsMap."ROOT" = propertyMap
    def strJson = """{"ROOT":{"fav-animal":{"name":"fav-animal","value":"unicorn"}}}"""

    expect:
    strJson.bytes == serializer.serialize(nsMap)
  }

  def "deserialize to map of config properties"() {

    def json = """{"hdfs-client":{"dfs.namenode.http-address.hdfs.nn2":{"name":"dfs.namenode.http-address.hdfs.nn2","value":"namenode2.hdfs.mesos:50070"},"dfs.client.failover.proxy.provider.hdfs":{"name":"dfs.client.failover.proxy.provider.hdfs","value":"org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider"}}}"""

    TypeReference<HashMap<String, HashMap<String, ConfigProperty>>> typeRef =
        new TypeReference<HashMap<String, HashMap<String, ConfigProperty>>>() {
        };
    def bytes = json.getBytes(Charset.defaultCharset())
    def map = serializer.deserialize(bytes)

    expect:
    map."hdfs-client"."dfs.namenode.http-address.hdfs.nn2".name == new ConfigProperty("dfs.namenode.http-address.hdfs.nn2", "namenode2.hdfs.mesos:50070").name
  }
}
