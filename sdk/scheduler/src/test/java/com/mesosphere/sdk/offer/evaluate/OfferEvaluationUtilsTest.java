package com.mesosphere.sdk.offer.evaluate;

import java.util.Optional;
import java.util.UUID;

import org.apache.mesos.Protos;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.mesosphere.sdk.offer.Constants;
import com.mesosphere.sdk.offer.MesosResource;
import com.mesosphere.sdk.offer.MesosResourcePool;
import com.mesosphere.sdk.offer.OfferRecommendation;
import com.mesosphere.sdk.offer.ReserveOfferRecommendation;
import com.mesosphere.sdk.offer.ResourceBuilder;
import com.mesosphere.sdk.offer.ResourceUtils;
import com.mesosphere.sdk.offer.UnreserveOfferRecommendation;
import com.mesosphere.sdk.offer.ValueUtils;
import com.mesosphere.sdk.offer.evaluate.OfferEvaluationUtils.ReserveEvaluationOutcome;
import com.mesosphere.sdk.specification.DefaultResourceSpec;
import com.mesosphere.sdk.specification.ResourceSpec;
import com.mesosphere.sdk.testutils.DefaultCapabilitiesTestSuite;

import static org.mockito.Mockito.*;

public class OfferEvaluationUtilsTest extends DefaultCapabilitiesTestSuite {

    private static final String RESOURCE_NAME = "blocks";
    private static final String ROLE = "svc-role";
    private static final String PRINCIPAL = "svc-principal";

    @Mock OfferEvaluationStage mockStage;
    @Mock MesosResourcePool mockPool;
    @Mock ResourceSpec mockResource;

    @Before
    public void beforeAll() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testResourceInsufficient() {
        testResourceInsufficient(Optional.empty(), Optional.empty());
        testResourceInsufficient(Optional.empty(), Optional.of("foo"));

        testResourceInsufficient(Optional.of(UUID.randomUUID().toString()), Optional.empty());
        testResourceInsufficient(Optional.of(UUID.randomUUID().toString()), Optional.of("foo"));
    }

    private void testResourceInsufficient(Optional<String> resourceId, Optional<String> namespace) {
        Protos.Value desired = getValue(5);

        if (resourceId.isPresent()) {
            when(mockPool.consumeReserved(RESOURCE_NAME, desired, resourceId.get())).thenReturn(Optional.empty());
        } else {
            when(mockPool.consumeReservableMerged(RESOURCE_NAME, desired, Constants.ANY_ROLE)).thenReturn(Optional.empty());
        }

        ReserveEvaluationOutcome outcome = OfferEvaluationUtils.evaluateSimpleResource(
                mockStage, getResourceSpec(desired), resourceId, namespace, mockPool);
        Assert.assertFalse(outcome.getEvaluationOutcome().isPassing());

        Assert.assertTrue(outcome.getEvaluationOutcome().getOfferRecommendations().isEmpty());
        Assert.assertFalse(outcome.getResourceId().isPresent());
    }

    @Test
    public void testResourceSufficient() {
        testResourceSufficient(Optional.empty(), Optional.empty());
        testResourceSufficient(Optional.empty(), Optional.of("foo"));

        testResourceSufficient(Optional.of(UUID.randomUUID().toString()), Optional.empty());
        testResourceSufficient(Optional.of(UUID.randomUUID().toString()), Optional.of("foo"));
    }

    private void testResourceSufficient(Optional<String> resourceId, Optional<String> namespace) {
        Protos.Value desired = getValue(5);

        ResourceSpec resourceSpec = getResourceSpec(desired);
        if (resourceId.isPresent()) {
            when(mockPool.consumeReserved(RESOURCE_NAME, desired, resourceId.get()))
                    .thenReturn(Optional.of(getMesosResource(resourceSpec, resourceId.get(), namespace)));
        } else {
            when(mockPool.consumeReservableMerged(RESOURCE_NAME, desired, Constants.ANY_ROLE))
                    .thenReturn(Optional.of(getMesosResource(desired)));
        }

        ReserveEvaluationOutcome outcome =
                OfferEvaluationUtils.evaluateSimpleResource(mockStage, resourceSpec, resourceId, namespace, mockPool);
        Assert.assertTrue(outcome.getEvaluationOutcome().isPassing());

        if (resourceId.isPresent()) {
            Assert.assertTrue(outcome.getEvaluationOutcome().getOfferRecommendations().isEmpty());
            Assert.assertEquals(resourceId.get(), outcome.getResourceId().get());
        } else {
            OfferRecommendation recommendation = outcome.getEvaluationOutcome().getOfferRecommendations().get(0);
            Assert.assertTrue(recommendation instanceof ReserveOfferRecommendation);
            Assert.assertTrue(outcome.getResourceId().isPresent());
            Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
            Assert.assertEquals(desired.getScalar(), resource.getScalar());
            if (namespace.isPresent()) {
                Assert.assertEquals(namespace.get(), ResourceUtils.getNamespace(resource).get());
            } else {
                Assert.assertFalse(ResourceUtils.getNamespace(resource).isPresent());
            }
        }
    }

    @Test
    public void testResourceIncreaseSufficient() {
        testResourceIncreaseSufficient(Optional.empty());
        testResourceIncreaseSufficient(Optional.of("foo"));
    }

    private void testResourceIncreaseSufficient(Optional<String> namespace) {
        String resourceId = UUID.randomUUID().toString();
        Protos.Value current = getValue(4);
        Protos.Value desired = getValue(5);
        Protos.Value toAdd = ValueUtils.subtract(desired, current);

        ResourceSpec resourceSpec = getResourceSpec(desired);
        when(mockPool.consumeReserved(RESOURCE_NAME, desired, resourceId))
                .thenReturn(Optional.of(getMesosResource(getResourceSpec(current), resourceId, namespace)));
        when(mockPool.consumeReservableMerged(RESOURCE_NAME, toAdd, Constants.ANY_ROLE))
                .thenReturn(Optional.of(getMesosResource(toAdd)));

        ReserveEvaluationOutcome outcome =
                OfferEvaluationUtils.evaluateSimpleResource(mockStage, resourceSpec, Optional.of(resourceId), namespace, mockPool);
        Assert.assertTrue(outcome.getEvaluationOutcome().isPassing());

        OfferRecommendation recommendation = outcome.getEvaluationOutcome().getOfferRecommendations().get(0);
        Assert.assertTrue(recommendation instanceof ReserveOfferRecommendation);
        Assert.assertTrue(outcome.getResourceId().isPresent());
        Protos.Resource resource = recommendation.getOperation().getReserve().getResources(0);
        Assert.assertEquals(toAdd.getScalar(), resource.getScalar());
        if (namespace.isPresent()) {
            Assert.assertEquals(namespace.get(), ResourceUtils.getNamespace(resource).get());
        } else {
            Assert.assertFalse(ResourceUtils.getNamespace(resource).isPresent());
        }
    }

    @Test
    public void testResourceIncreaseInsufficient() {
        testResourceIncreaseInsufficient(Optional.empty());
        testResourceIncreaseInsufficient(Optional.of("foo"));
    }

    private void testResourceIncreaseInsufficient(Optional<String> namespace) {
        String resourceId = UUID.randomUUID().toString();
        Protos.Value current = getValue(4);
        Protos.Value desired = getValue(5);
        Protos.Value toAdd = ValueUtils.subtract(desired, current);

        ResourceSpec resourceSpec = getResourceSpec(desired);
        when(mockPool.consumeReserved(RESOURCE_NAME, desired, resourceId))
                .thenReturn(Optional.of(getMesosResource(getResourceSpec(current), resourceId, namespace)));
        when(mockPool.consumeReservableMerged(RESOURCE_NAME, toAdd, Constants.ANY_ROLE))
                .thenReturn(Optional.empty());

        ReserveEvaluationOutcome outcome =
                OfferEvaluationUtils.evaluateSimpleResource(mockStage, resourceSpec, Optional.of(resourceId), namespace, mockPool);
        Assert.assertFalse(outcome.getEvaluationOutcome().isPassing());

        Assert.assertTrue(outcome.getEvaluationOutcome().getOfferRecommendations().isEmpty());
        Assert.assertFalse(outcome.getResourceId().isPresent());
    }

    @Test
    public void testResourceDecrease() {
        testResourceDecrease(Optional.empty());
        testResourceDecrease(Optional.of("foo"));
    }

    private void testResourceDecrease(Optional<String> namespace) {
        String resourceId = UUID.randomUUID().toString();
        Protos.Value current = getValue(5);
        Protos.Value desired = getValue(4);
        Protos.Value toSubtract = ValueUtils.subtract(current, desired);

        ResourceSpec resourceSpec = getResourceSpec(desired);
        when(mockPool.consumeReserved(RESOURCE_NAME, desired, resourceId))
                .thenReturn(Optional.of(getMesosResource(getResourceSpec(current), resourceId, namespace)));
        when(mockPool.consumeReservableMerged(RESOURCE_NAME, desired, Constants.ANY_ROLE))
                .thenReturn(Optional.of(getMesosResource(toSubtract)));

        ReserveEvaluationOutcome outcome =
                OfferEvaluationUtils.evaluateSimpleResource(mockStage, resourceSpec, Optional.of(resourceId), namespace, mockPool);
        Assert.assertTrue(outcome.getEvaluationOutcome().isPassing());

        OfferRecommendation recommendation = outcome.getEvaluationOutcome().getOfferRecommendations().get(0);
        Assert.assertTrue(recommendation instanceof UnreserveOfferRecommendation);
        Assert.assertTrue(outcome.getResourceId().isPresent());
        Protos.Resource resource = recommendation.getOperation().getUnreserve().getResources(0);
        Assert.assertEquals(toSubtract.getScalar(), resource.getScalar());
        if (namespace.isPresent()) {
            Assert.assertEquals(namespace.get(), ResourceUtils.getNamespace(resource).get());
        } else {
            Assert.assertFalse(ResourceUtils.getNamespace(resource).isPresent());
        }
    }

    private static Protos.Value getValue(double value) {
        Protos.Value.Builder valueBuilder = Protos.Value.newBuilder().setType(Protos.Value.Type.SCALAR);
        valueBuilder.getScalarBuilder().setValue(value);
        return valueBuilder.build();
    }

    private static ResourceSpec getResourceSpec(Protos.Value value) {
        return new DefaultResourceSpec(RESOURCE_NAME, value, ROLE, null, PRINCIPAL);
    }

    private static MesosResource getMesosResource(ResourceSpec resourceSpec, String resourceId, Optional<String> namespace) {
        return new MesosResource(ResourceBuilder.fromSpec(resourceSpec, Optional.of(resourceId), namespace).build());
    }

    private static MesosResource getMesosResource(Protos.Value value) {
        return new MesosResource(ResourceBuilder.fromUnreservedValue(RESOURCE_NAME, value).build());
    }
}
