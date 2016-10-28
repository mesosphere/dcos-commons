package org.apache.mesos.offer.constrain;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.Resource;
import org.apache.mesos.Protos.Value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

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

    public static final PlacementRule ALL = new AllTestRule();
    public static final PlacementRule NONE = new NoneTestRule();
    public static final PlacementRule REMOVE_FIRST = new RemoveFirstTestRule();
    public static final PlacementRule REMOVE_LAST = new RemoveLastTestRule();

    private static class AllTestRule implements PlacementRule {
        @JsonCreator
        public AllTestRule() { }

        @Override
        public Offer filter(Offer offer) {
            return offer;
        }

        @Override
        public boolean equals(Object o) { return EqualsBuilder.reflectionEquals(this, o); }

        @Override
        public int hashCode() { return HashCodeBuilder.reflectionHashCode(this); }
    }

    private static class NoneTestRule implements PlacementRule {
        @JsonCreator
        public NoneTestRule() { }

        @Override
        public Offer filter(Offer offer) {
            return offer.toBuilder().clearResources().build();
        }

        @Override
        public boolean equals(Object o) { return EqualsBuilder.reflectionEquals(this, o); }

        @Override
        public int hashCode() { return HashCodeBuilder.reflectionHashCode(this); }
    };

    private static class RemoveFirstTestRule implements PlacementRule {
        @JsonCreator
        public RemoveFirstTestRule() { }

        @Override
        public Offer filter(Offer offer) {
            if (offer.getResourcesCount() == 0) {
                return offer;
            }
            return offer.toBuilder().removeResources(0).build();
        }

        @Override
        public boolean equals(Object o) { return EqualsBuilder.reflectionEquals(this, o); }

        @Override
        public int hashCode() { return HashCodeBuilder.reflectionHashCode(this); }
    };

    private static class RemoveLastTestRule implements PlacementRule {
        @JsonCreator
        public RemoveLastTestRule() { }

        @Override
        public Offer filter(Offer offer) {
            int count = offer.getResourcesCount();
            if (count == 0) {
                return offer;
            }
            return offer.toBuilder().removeResources(count - 1).build();
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
