package com.mesosphere.sdk.offer.taskdata;

import com.google.protobuf.TextFormat;
import org.apache.mesos.Protos;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tools for converting {@link Attribute}s into strings. Spec is defined by Mesos documentation at:
 * http://mesos.apache.org/documentation/latest/attributes-resources/
 */
public final class AttributeStringUtils {

  private static final String ATTRIBUTE_LIST_SEPARATOR = ";";

  private static final char ATTRIBUTE_KEYVAL_SEPARATOR = ':';

  private AttributeStringUtils() {
    // do not instantiate
  }

  public static List<String> toStringList(String joinedAttributes) {
    StringTokenizer tokenizer = new StringTokenizer(joinedAttributes, ATTRIBUTE_LIST_SEPARATOR);
    List<String> tokens = new ArrayList<>();
    while (tokenizer.hasMoreTokens()) {
      tokens.add(tokenizer.nextToken());
    }
    return tokens;
  }

  /**
   * Converts the provided name + value strings to an attribute string.
   *
   * @see #split(String) which does the opposite
   */
  public static String join(String name, String value) {
    return String.join(String.valueOf(ATTRIBUTE_KEYVAL_SEPARATOR), name, value);
  }

  /**
   * Returns the name and value from the provided attribute string.
   *
   * @see #join(String, String) which does the opposite
   */
  public static NameValue split(String attribute) {
    String[] split = attribute.split(String.valueOf(ATTRIBUTE_KEYVAL_SEPARATOR), 2);
    if (split.length != 2) {
      throw new IllegalArgumentException(String.format(
          "Unable to split attribute into name%cvalue elements: attribute=%s result=%s",
          ATTRIBUTE_KEYVAL_SEPARATOR, attribute, Arrays.toString(split)));
    }
    return new NameValue(split[0], split[1]);
  }

  /**
   * Converts the provided list of zero or more attributes into a string suitable for comparison.
   *
   * @throws IllegalArgumentException if some part of the provided attributes couldn't be
   *                                  serialized
   */
  public static String toString(List<Protos.Attribute> attributes) throws IllegalArgumentException {
    StringJoiner joiner = new StringJoiner(ATTRIBUTE_LIST_SEPARATOR);
    for (Protos.Attribute attribute : attributes) {
      joiner.add(toString(attribute));
    }
    return joiner.toString();
  }

  /**
   * Converts the provided attribute's name + value into a string which follows the format defined
   * by Mesos.
   *
   * @see #valueString(Attribute)
   */
  public static String toString(Protos.Attribute attribute) throws IllegalArgumentException {
    return String.format("%s%c%s",
        attribute.getName(), ATTRIBUTE_KEYVAL_SEPARATOR, toString(toValue(attribute)));
  }

  /**
   * Converts the provided attribute's value into a string which follows the format defined by
   * Mesos:
   * <code>
   * attributes : attribute ( ";" attribute )*
   * attribute : text ":" ( scalar | range | text )
   * text : [a-zA-Z0-9_/.-]
   * scalar : floatValue
   * floatValue : ( intValue ( "." intValue )? ) | ...
   * intValue : [0-9]+
   * range : "[" rangeValue ( "," rangeValue )* "]"
   * rangeValue : scalar "-" scalar
   * set : "{" text ( "," text )* "}"
   * </code>
   * <p>
   * NOTE that it is difficult if not impossible to consistently perform the inverse of this
   * operation. For example, how can you tell if something is supposed to be a SCALAR value or a
   * TEXT value? [0-9.]+ is valid in both cases! Your best hope is to consistently convert to
   * string, and then compare strings...
   *
   * @throws IllegalArgumentException if some part of the provided attributes couldn't be
   *                                  serialized
   */
  public static String toString(Protos.Value value) throws IllegalArgumentException {
    StringBuffer buf = new StringBuffer();
    switch (value.getType()) {
      case RANGES:
        // "ports:[21000-24000,30000-34000]"
        buf.append('[');
        buf.append(value
            .getRanges()
            .getRangeList()
            .stream()
            .map(range -> String.format("%d-%d", range.getBegin(), range.getEnd()))
            .collect(Collectors.joining(","))
        );
        buf.append(']');
        break;
      case SCALAR:
        // according to mesos.proto: "Mesos keeps three decimal digits of precision ..."
        // let's just ensure that we're producing consistent strings.
        buf.append(String.format(Locale.ROOT, "%.3f", value.getScalar().getValue()));
        break;
      case SET:
        // "bugs(debug_role):{a,b,c}"
        buf.append('{');
        buf.append(String.join(",", value.getSet().getItemList()));
        buf.append('}');
        break;
      case TEXT:
        // "key:value"
        buf.append(value.getText().getValue());
        break;
      default:
        throw new IllegalArgumentException(
            "Unsupported value type: " + TextFormat.shortDebugString(value)
        );
    }
    return buf.toString();
  }

  /**
   * Copies the value data in the provided Attribute the form of a Value object.
   */
  public static Protos.Value toValue(Protos.Attribute attribute) {
    Protos.Value.Builder builder = Protos.Value.newBuilder()
        .setType(attribute.getType());
    if (attribute.hasRanges()) {
      builder.setRanges(attribute.getRanges());
    }
    if (attribute.hasScalar()) {
      builder.setScalar(attribute.getScalar());
    }
    if (attribute.hasSet()) {
      builder.setSet(attribute.getSet());
    }
    if (attribute.hasText()) {
      builder.setText(attribute.getText());
    }
    return builder.build();
  }

  /**
   * A utility class which pairs an attribute name with an attribute value.
   */
  public static final class NameValue {
    public final String name;

    public final String value;

    private NameValue(String name, String value) {
      this.name = name;
      this.value = value;
    }
  }
}
