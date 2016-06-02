package org.apache.mesos.collections;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 */
public class MapUtil {

  public static Map<String, String> propertyMapFilter(Properties properties, Predicate<String> predicate) {
    if (properties == null) {
      return new HashMap<>();
    }
    if (predicate == null) {
      return Maps.fromProperties(properties);
    }

    return Maps.filterKeys(Maps.fromProperties(properties), predicate);
  }
}
