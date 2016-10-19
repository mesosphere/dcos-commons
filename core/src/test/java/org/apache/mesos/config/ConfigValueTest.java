package org.apache.mesos.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link ConfigValue}.
 */
public class ConfigValueTest {

    // String

    @Test
    public void testStringRequired() {
        assertEquals("hey", val("hey").requiredString());
    }

    @Test
    public void testStringOptional() {
        assertEquals("hey", val("hey").optionalString("hi"));
    }

    @Test(expected=IllegalStateException.class)
    public void testStringMissingRequired() {
        val(null).requiredString();
    }

    @Test
    public void testStringMissingOptional() {
        assertEquals("hi", val(null).optionalString("hi"));
    }

    @Test
    public void testStringValues() {
        assertEquals("", val("").requiredString());
        assertEquals("a", val("a").requiredString());
        assertEquals("ab", val("ab").requiredString());
        assertEquals("", val("").optionalString("hi"));
        assertEquals("a", val("a").optionalString("hi"));
        assertEquals("ab", val("ab").optionalString("hi"));
        assertEquals("hi", val(null).optionalString("hi"));
    }

    // Boolean

    @Test
    public void testBooleanRequired() {
        assertEquals(true, val("t").requiredBoolean());
        assertEquals(false, val("f").requiredBoolean());
    }

    @Test
    public void testBooleanOptional() {
        assertEquals(true, val("t").optionalBoolean(false));
        assertEquals(false, val("f").optionalBoolean(true));
    }

    @Test(expected=IllegalStateException.class)
    public void testBooleanMissingRequired() {
        val(null).requiredBoolean();
    }

    @Test
    public void testBooleanMissingOptional() {
        assertEquals(true, val(null).optionalBoolean(true));
        assertEquals(false, val(null).optionalBoolean(false));
    }

    @Test
    public void testBooleanValues() {
        assertEquals(false, val("").requiredBoolean());
        assertEquals(false, val("a").requiredBoolean());
        assertEquals(false, val("one").requiredBoolean());
        assertEquals(false, val("zero").requiredBoolean());

        assertEquals(true, val("t").requiredBoolean());
        assertEquals(true, val("T").requiredBoolean());
        assertEquals(true, val("true").requiredBoolean());
        assertEquals(true, val("TRUE").requiredBoolean());
        assertEquals(true, val("tRuE").requiredBoolean());
        assertEquals(true, val("TrUe").requiredBoolean());
        assertEquals(true, val("y").requiredBoolean());
        assertEquals(true, val("Y").requiredBoolean());
        assertEquals(true, val("yes").requiredBoolean());
        assertEquals(true, val("YES").requiredBoolean());
        assertEquals(true, val("yEs").requiredBoolean());
        assertEquals(true, val("YeS").requiredBoolean());
        assertEquals(true, val("1").requiredBoolean());
        assertEquals(true, val("100").requiredBoolean());

        assertEquals(false, val("f").requiredBoolean());
        assertEquals(false, val("F").requiredBoolean());
        assertEquals(false, val("false").requiredBoolean());
        assertEquals(false, val("FALSE").requiredBoolean());
        assertEquals(false, val("fAlSe").requiredBoolean());
        assertEquals(false, val("FaLsE").requiredBoolean());
        assertEquals(false, val("n").requiredBoolean());
        assertEquals(false, val("N").requiredBoolean());
        assertEquals(false, val("no").requiredBoolean());
        assertEquals(false, val("NO").requiredBoolean());
        assertEquals(false, val("nO").requiredBoolean());
        assertEquals(false, val("No").requiredBoolean());
        assertEquals(false, val("0").requiredBoolean());
        assertEquals(false, val("000").requiredBoolean());
    }

    // Int

    @Test
    public void testIntRequired() {
        assertEquals(123, val("123").requiredInt());
        assertEquals(456, val("456").requiredInt());
    }

    @Test
    public void testIntOptional() {
        assertEquals(500, val("500").optionalInt(2));
        assertEquals(14, val("14").optionalInt(3));
    }

    @Test(expected=IllegalStateException.class)
    public void testIntMissingRequired() {
        val(null).requiredInt();
    }

    @Test
    public void testIntMissingOptional() {
        assertEquals(5, val(null).optionalInt(5));
    }

    @Test(expected=NumberFormatException.class)
    public void testIntDecimalFails() {
        val("43.67").requiredInt();
    }

    @Test(expected=NumberFormatException.class)
    public void testIntEmptyFails() {
        val("").requiredInt();
    }

    @Test(expected=NumberFormatException.class)
    public void testIntWordDecimalFails() {
        val("989 studios").requiredInt();
    }

    @Test
    public void testIntValues() {
        assertEquals(123, val("123").requiredInt());
        assertEquals(-123, val("-123").requiredInt());
        assertEquals(123, val("0123").requiredInt());
        assertEquals(-123, val("-0123").requiredInt());
        assertEquals(0, val("0").requiredInt());
        assertEquals(0, val("000").requiredInt());
    }

    // Long

    @Test
    public void testLongRequired() {
        assertEquals(1234567890123456789L, val("1234567890123456789").requiredLong());
        assertEquals(456, val("456").requiredLong());
    }

    @Test
    public void testLongOptional() {
        assertEquals(500, val("500").optionalLong(2));
        assertEquals(14, val("14").optionalLong(3));
    }

    @Test(expected=IllegalStateException.class)
    public void testLongMissingRequired() {
        val(null).requiredLong();
    }

    @Test
    public void testLongMissingOptional() {
        assertEquals(5, val(null).optionalLong(5));
        assertEquals(1234567890123456789L, val(null).optionalLong(1234567890123456789L));
    }

    @Test(expected=NumberFormatException.class)
    public void testLongDecimalFails() {
        val("43.67").requiredLong();
    }

    @Test(expected=NumberFormatException.class)
    public void testLongEmptyFails() {
        val("").requiredLong();
    }

    @Test(expected=NumberFormatException.class)
    public void testLongWordDecimalFails() {
        val("989 studios").requiredLong();
    }

    @Test
    public void testLongValues() {
        assertEquals(123, val("123").requiredLong());
        assertEquals(-123, val("-123").requiredLong());
        assertEquals(123, val("0123").requiredLong());
        assertEquals(-123, val("-0123").requiredLong());
        assertEquals(0, val("0").requiredLong());
        assertEquals(0, val("000").requiredLong());
    }

    // Double

    @Test
    public void testDoubleRequired() {
        assertEquals(123.456, val("123.456").requiredDouble(), 0.001);
        assertEquals(456., val("456").requiredDouble(), 0.001);
    }

    @Test
    public void testDoubleOptional() {
        assertEquals(500.3, val("500.3").optionalDouble(2), 0.001);
        assertEquals(14.8, val("14.8").optionalDouble(3), 0.001);
    }

    @Test(expected=IllegalStateException.class)
    public void testDoubleMissingRequired() {
        val(null).requiredDouble();
    }

    @Test
    public void testDoubleMissingOptional() {
        assertEquals(5, val(null).optionalDouble(5), 0.);
        assertEquals(1982731.88, val(null).optionalDouble(1982731.88), 0.);
    }

    @Test(expected=NumberFormatException.class)
    public void testDoubleEmptyFails() {
        val("").requiredDouble();
    }

    @Test(expected=NumberFormatException.class)
    public void testDoubleWordDecimalFails() {
        val("989.89 studios").requiredDouble();
    }

    @Test
    public void testDoubleValues() {
        assertEquals(123, val("123").requiredDouble(), 0.001);
        assertEquals(-123, val("-123").requiredDouble(), 0.001);
        assertEquals(123, val("0123").requiredDouble(), 0.001);
        assertEquals(-123, val("-0123").requiredDouble(), 0.001);
        assertEquals(.123, val("0.123").requiredDouble(), 0.001);
        assertEquals(-.123, val("-0.123").requiredDouble(), 0.001);
        assertEquals(.0123, val(".0123").requiredDouble(), 0.001);
        assertEquals(-.0123, val("-.0123").requiredDouble(), 0.001);
        assertEquals(0, val("0").requiredDouble(), 0.001);
        assertEquals(0, val("000").requiredDouble(), 0.001);
        assertEquals(0, val(".0").requiredDouble(), 0.001);
    }

    private static ConfigValue val(String value) {
        return new ConfigValue("testval", value);
    }
}
