package org.apache.mesos.stream

import com.fasterxml.jackson.core.type.TypeReference
import org.apache.mesos.config.ConfigProperty
import spock.lang.Specification

/**
 *
 */
class JsonSerializerSpec extends Specification {

  def mapJsonString = """{"config.property.1":"test","key":"value"}"""
  def map2JsonString = """{"map":{"config.property.1":"test","key":"value"}}"""

  def serializer = new JsonSerializer()

  def "serialiation of maps"() {
    def map = [:]
    map."config.property.1" = "test"
    map."key" = "value"

    def map2 = [:]
    map2."map" = map


    expect:
    serializer.serialize(map) == mapJsonString
    serializer.serialize(map2) == map2JsonString
  }

  def "de-serializing of maps"() {
    def map = serializer.deserialize(mapJsonString, Map.class);
    def map2 = serializer.deserialize(map2JsonString, Map.class);

    expect:
    map.key == "value"
    map2.map instanceof Map
  }

  def "config property maps"() {

    def json = """{"hdfs-client":{"dfs.namenode.http-address.hdfs.nn2":{"name":"dfs.namenode.http-address.hdfs.nn2","value":"namenode2.hdfs.mesos:50070"},"dfs.client.failover.proxy.provider.hdfs":{"name":"dfs.client.failover.proxy.provider.hdfs","value":"org.apache.hadoop.hdfs.server.namenode.ha.ConfiguredFailoverProxyProvider"}}}"""

    TypeReference<HashMap<String, HashMap<String, ConfigProperty>>> typeRef =
        new TypeReference<HashMap<String, HashMap<String, ConfigProperty>>>() {
        };

    def map = serializer.deserialize(json, typeRef)

    expect:
    map."hdfs-client"."dfs.namenode.http-address.hdfs.nn2".name == new ConfigProperty("dfs.namenode.http-address.hdfs.nn2", "namenode2.hdfs.mesos:50070").name
  }
}
