package org.apache.mesos.scheduler.plan.strategy;

import org.apache.mesos.scheduler.plan.Element;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * A CanaryStrategy blocks employment entirely by default until human intervention through the {@link #proceed()} call.
 * After a single {@link #proceed()} call it again blocks until human intervention.  Any further calls to
 * {@link #proceed()} will indicate the strategy should continue without further intervention.
 *
 * @param <C> is the type of the child elements of the provided parent {@link Element} when calling
 * {@link CanaryStrategy#getCandidates(Element, Collection)}.
 */
public class CanaryStrategy<C extends Element> extends SerialStrategy<C> {
    AtomicBoolean initialized = new AtomicBoolean(false);

    @Override
    public Collection<C> getCandidates(Element<C> parentElement, Collection<String> dirtyAssets) {
        if (!initialized.get()) {
            List<Element> pendingElements = parentElement.getChildren().stream()
                    .filter(element -> element.isPending())
                    .collect(Collectors.toList());

            for (int i = 0; i < 2; i++) {
                pendingElements.get(i).getStrategy().interrupt();
            }

            initialized.set(true);
        }

        return super.getCandidates(parentElement, dirtyAssets);
    }
}
