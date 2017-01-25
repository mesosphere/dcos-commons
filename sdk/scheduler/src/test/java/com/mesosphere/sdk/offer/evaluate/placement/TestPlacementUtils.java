package com.mesosphere.sdk.offer.evaluate.placement;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Protos.Value;
import com.mesosphere.sdk.config.SerializationUtils;
import com.mesosphere.sdk.offer.OfferRequirement;
import com.mesosphere.sdk.offer.evaluate.EvaluationOutcome;
import com.mesosphere.sdk.specification.DefaultServiceSpec;

import java.util.Collection;

/**
 * Utilities for testing placement constraints.
 */
public class TestPlacementUtils {

    public static final Resource RESOURCE_1;
    public static final Resource RESOURCE_2;
    public static final Resource RESOURCE_3;
    public static final Resource RESOURCE_4;

    public static final ImmutableCollection<Resource> RESOURCES;

    static {
        Resource.Builder b = Resource.newBuilder().setName("1").setType(Value.Type.RANGES);
        b.getRangesBuilder().addRangeBuilder().setBegin(5).setEnd(6);
        b.getRangesBuilder().addRangeBuilder().setBegin(10).setEnd(12);
        RESOURCE_1 = b.build();

        b = Resource.newBuilder().setName("2").setType(Value.Type.SCALAR);
        b.getScalarBuilder().setValue(123.456);
        RESOURCE_2 = b.build();

        b = Resource.newBuilder().setName("3").setType(Value.Type.SET);
        b.getSetBuilder().addItem("foo").addItem("bar").addItem("baz");
        RESOURCE_3 = b.build();

        b = Resource.newBuilder().setName("4").setType(Value.Type.RANGES);
        b.getRangesBuilder().addRangeBuilder().setBegin(7).setEnd(8);
        b.getRangesBuilder().addRangeBuilder().setBegin(10).setEnd(12);
        RESOURCE_4 = b.build();

        RESOURCES = new ImmutableList.Builder<Resource>().add(RESOURCE_1, RESOURCE_2, RESOURCE_3, RESOURCE_4).build();
    }

    public static final PlacementRule PASS = new PassTestRule();
    public static final PlacementRule FAIL = new FailTestRule();

    public static final ObjectMapper OBJECT_MAPPER;
    static {
        OBJECT_MAPPER = SerializationUtils.registerDefaultModules(new ObjectMapper());
        for (Class<?> c : DefaultServiceSpec.Factory.getDefaultRegisteredSubtypes()) {
            OBJECT_MAPPER.registerSubtypes(c);
        }
        OBJECT_MAPPER.registerSubtypes(PassTestRule.class);
        OBJECT_MAPPER.registerSubtypes(FailTestRule.class);
    }

    private static class PassTestRule implements PlacementRule {
        @JsonCreator
        public PassTestRule() { }

        @Override
        public EvaluationOutcome filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
            return EvaluationOutcome.pass(this, "test pass");
        }

        @Override
        public boolean equals(Object o) { return EqualsBuilder.reflectionEquals(this, o); }

        @Override
        public int hashCode() { return HashCodeBuilder.reflectionHashCode(this); }
    }

    private static class FailTestRule implements PlacementRule {
        @JsonCreator
        public FailTestRule() { }

        @Override
        public EvaluationOutcome filter(Offer offer, OfferRequirement offerRequirement, Collection<TaskInfo> tasks) {
            return EvaluationOutcome.fail(this, "test fail");
        }

        @Override
        public boolean equals(Object o) { return EqualsBuilder.reflectionEquals(this, o); }

        @Override
        public int hashCode() { return HashCodeBuilder.reflectionHashCode(this); }
    };

    private TestPlacementUtils() {
        // do not instantiate
    }
}
