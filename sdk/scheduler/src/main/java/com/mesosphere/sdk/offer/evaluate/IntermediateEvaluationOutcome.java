package com.mesosphere.sdk.offer.evaluate;

import com.mesosphere.sdk.offer.OfferRecommendation;
import org.apache.mesos.Protos;

import java.util.List;

import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.fail;
import static com.mesosphere.sdk.offer.evaluate.EvaluationOutcome.pass;

/**
 * Created by gabriel on 5/23/17.
 */
public class IntermediateEvaluationOutcome {
    private final boolean passed;
    private final Protos.Resource resource;
    private final List<OfferRecommendation> recommendations;
    private final List<String> messages;

    public IntermediateEvaluationOutcome(
            boolean passed,
            Protos.Resource resource,
            List<OfferRecommendation> recommendations,
            List<String> messages) {
        this.passed = passed;
        this.resource = resource;
        this.recommendations = recommendations;
        this.messages = messages;
    }

    public boolean hasPassed() {
        return passed;
    }

    public Protos.Resource getResource() {
        return resource;
    }

    public List<OfferRecommendation> getRecommendations() {
        return recommendations;
    }

    public List<String> getMessages() {
        return messages;
    }

    public EvaluationOutcome toEvaluationOutcome(Object source) {
        if (hasPassed()) {
            return pass(
                    source,
                    getRecommendations(),
                    getMessages().toString());
        } else {
            return fail(
                    source,
                    getMessages().toString());
        }
    }
}
