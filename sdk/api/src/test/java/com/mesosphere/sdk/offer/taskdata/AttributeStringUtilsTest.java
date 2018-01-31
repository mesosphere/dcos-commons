package com.mesosphere.sdk.offer.taskdata;

import org.apache.mesos.Protos.Attribute;
import org.apache.mesos.Protos.Value.Type;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link AttributeStringUtils}.
 *
 * Samples:
 * --resources='cpus:24;gpus:2;mem:24576;disk:409600;ports:[21000-24000,30000-34000];bugs(debug_role):{a,b,c}'
 * --attributes='rack:abc;zone:west;os:centos5;level:10;keys:[1000-1500]'
 */
public class AttributeStringUtilsTest {

    @Test
    public void testRangeAttributeString() {
        Attribute.Builder attr = Attribute.newBuilder().setType(Type.RANGES).setName("ram");
        attr.getRangesBuilder().addRangeBuilder().setBegin(1).setEnd(2);
        assertEquals("ram:[1-2]", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.RANGES).setName("ports");
        attr.getRangesBuilder().addRangeBuilder().setBegin(1).setEnd(2);
        attr.getRangesBuilder().addRangeBuilder().setBegin(-321).setEnd(-123);
        attr.getRangesBuilder().addRangeBuilder().setBegin(21000).setEnd(24000);
        attr.getRangesBuilder().addRangeBuilder().setBegin(0).setEnd(0);
        attr.getRangesBuilder().addRangeBuilder().setBegin(30000).setEnd(34000);
        attr.getRangesBuilder().addRangeBuilder().setBegin(321).setEnd(123);
        assertEquals("ports:[1-2,-321--123,21000-24000,0-0,30000-34000,321-123]",
                AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.RANGES).setName("ram");
        attr.getRangesBuilder().addRangeBuilder().setBegin(0).setEnd(0);
        assertEquals("ram:[0-0]", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.RANGES).setName("disk");
        attr.getRangesBuilder().addRangeBuilder().setBegin(-321).setEnd(-123);
        assertEquals("disk:[-321--123]", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.RANGES).setName("");
        attr.getRangesBuilder().addRangeBuilder().setBegin(-321).setEnd(-123);
        assertEquals(":[-321--123]", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.RANGES).setName("empty");
        assertEquals("empty:[]", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.RANGES).setName("");
        assertEquals(":[]", AttributeStringUtils.toString(attr.build()));
    }

    @Test
    public void testScalarAttributeString() {
        Attribute.Builder attr = Attribute.newBuilder().setType(Type.SCALAR).setName("ram");
        attr.getScalarBuilder().setValue(0);
        assertEquals("ram:0.000", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.SCALAR).setName("ports");
        attr.getScalarBuilder().setValue(0.000);
        assertEquals("ports:0.000", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.SCALAR).setName("ports");
        attr.getScalarBuilder().setValue(0.0001);
        assertEquals("ports:0.000", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.SCALAR).setName("ports");
        attr.getScalarBuilder().setValue(0.0005);
        assertEquals("ports:0.001", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.SCALAR).setName("rounddown");
        attr.getScalarBuilder().setValue(-1.99999);
        assertEquals("rounddown:-2.000", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.SCALAR).setName("roundup1");
        attr.getScalarBuilder().setValue(1.99999);
        assertEquals("roundup1:2.000", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.SCALAR).setName("");
        attr.getScalarBuilder().setValue(1.99999);
        assertEquals(":2.000", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.SCALAR).setName("roundup2");
        attr.getScalarBuilder().setValue(999999.99999);
        assertEquals("roundup2:1000000.000", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.SCALAR).setName("empty");
        assertEquals("empty:0.000", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.SCALAR).setName("");
        assertEquals(":0.000", AttributeStringUtils.toString(attr.build()));
    }

    @Test
    public void testSetAttributeString() {
        Attribute.Builder attr = Attribute.newBuilder().setType(Type.SET).setName("ram");
        attr.getSetBuilder().addItem("");
        assertEquals("ram:{}", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.SET).setName("ports");
        attr.getSetBuilder().addItem("a").addItem("b").addItem("c");
        assertEquals("ports:{a,b,c}", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.SET).setName("disk");
        attr.getSetBuilder().addItem("-1").addItem("-2");
        assertEquals("disk:{-1,-2}", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.SET).setName("");
        attr.getSetBuilder().addItem("-1").addItem("-2");
        assertEquals(":{-1,-2}", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.SET).setName("empty");
        assertEquals("empty:{}", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.SET).setName("");
        assertEquals(":{}", AttributeStringUtils.toString(attr.build()));
    }

    @Test
    public void testTextAttributeString() {
        Attribute.Builder attr = Attribute.newBuilder().setType(Type.TEXT).setName("ram");
        attr.getTextBuilder().setValue(":::");
        assertEquals("ram::::", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.TEXT).setName("ram");
        attr.getTextBuilder().setValue("abc");
        assertEquals("ram:abc", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.TEXT).setName("");
        attr.getTextBuilder().setValue("123");
        assertEquals(":" + "123" /* workaround for lint thinking this is an IP */,
                AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.TEXT).setName("empty");
        assertEquals("empty:", AttributeStringUtils.toString(attr.build()));

        attr = Attribute.newBuilder().setType(Type.TEXT).setName("");
        assertEquals(":", AttributeStringUtils.toString(attr.build()));
    }

    @Test
    public void testMixedAttributeStrings() {
        List<Attribute> attrs = new ArrayList<>();
        assertEquals("", AttributeStringUtils.toString(attrs));

        Attribute.Builder attr = Attribute.newBuilder().setType(Type.TEXT).setName("ram");
        attr.getTextBuilder().setValue("");
        attrs.add(attr.build());
        assertEquals("ram:", AttributeStringUtils.toString(attrs));

        attr = Attribute.newBuilder().setType(Type.SET).setName("ports");
        attr.getSetBuilder().addItem("a").addItem("b").addItem("c");
        attrs.add(attr.build());
        assertEquals("ram:;ports:{a,b,c}", AttributeStringUtils.toString(attrs));

        attr = Attribute.newBuilder().setType(Type.SCALAR).setName("roundup1");
        attr.getScalarBuilder().setValue(1.99999);
        attrs.add(attr.build());
        assertEquals("ram:;ports:{a,b,c};roundup1:2.000", AttributeStringUtils.toString(attrs));

        attr = Attribute.newBuilder().setType(Type.RANGES).setName("disk");
        attr.getRangesBuilder().addRangeBuilder().setBegin(-321).setEnd(-123);
        attrs.add(attr.build());
        assertEquals("ram:;ports:{a,b,c};roundup1:2.000;disk:[-321--123]", AttributeStringUtils.toString(attrs));
    }

    @Test
    public void testSplitAttributeString() {
        assertTrue(AttributeStringUtils.toStringList("").isEmpty());

        List<String> strs = AttributeStringUtils.toStringList(
                "cpus:24;gpus:2;mem:24576;disk:409600;ports:[21000-24000,30000-34000];bugs(debug_role):{a,b,c}");
        List<String> expect = new ArrayList<>();
        expect.add("cpus:24");
        expect.add("gpus:2");
        expect.add("mem:24576");
        expect.add("disk:409600");
        expect.add("ports:[21000-24000,30000-34000]");
        expect.add("bugs(debug_role):{a,b,c}");
        assertEquals(expect, strs);

        strs = AttributeStringUtils.toStringList(
                "rack:abc;zone:west;os:centos5;level:10;keys:[1000-1500]");
        expect.clear();
        expect.add("rack:abc");
        expect.add("zone:west");
        expect.add("os:centos5");
        expect.add("level:10");
        expect.add("keys:[1000-1500]");
        assertEquals(expect, strs);
    }

    @Test
    public void testSplitJoinSingleAttribute() {
        AttributeStringUtils.NameValue nv = AttributeStringUtils.split(":");
        assertEquals("", nv.name);
        assertEquals("", nv.value);
        assertEquals(":", AttributeStringUtils.join(nv.name, nv.value));

        nv = AttributeStringUtils.split("foo:");
        assertEquals("foo", nv.name);
        assertEquals("", nv.value);
        assertEquals("foo:", AttributeStringUtils.join(nv.name, nv.value));

        nv = AttributeStringUtils.split(":bar");
        assertEquals("", nv.name);
        assertEquals("bar", nv.value);
        assertEquals(":bar", AttributeStringUtils.join(nv.name, nv.value));

        nv = AttributeStringUtils.split("foo:bar");
        assertEquals("foo", nv.name);
        assertEquals("bar", nv.value);
        assertEquals("foo:bar", AttributeStringUtils.join(nv.name, nv.value));

        nv = AttributeStringUtils.split("foo:bar:baz");
        assertEquals("foo", nv.name);
        assertEquals("bar:baz", nv.value);
        assertEquals("foo:bar:baz", AttributeStringUtils.join(nv.name, nv.value));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSplitSingleAttributeFails() {
        AttributeStringUtils.split("foobar");
    }
}
