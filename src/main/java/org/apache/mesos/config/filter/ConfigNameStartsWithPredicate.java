package org.apache.mesos.config.filter;


import com.google.common.base.Predicate;
import org.apache.mesos.config.ConfigProperty;

/**
 * Used to filter collections of ConfigProperties to only those with a property
 * name that start with a specific string.   Commonly used to get
 * all the configurations which belong to a class of configurations.
 */
public class ConfigNameStartsWithPredicate implements Predicate<ConfigProperty> {

  private String startWithString;

  public ConfigNameStartsWithPredicate(String startWithString) {
    this.startWithString = startWithString;
  }

  @Override
  public boolean apply(ConfigProperty property) {
    return property.getName().startsWith(startWithString);
  }
}
