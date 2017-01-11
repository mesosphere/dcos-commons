package com.mesosphere.sdk.scheduler.plan.strategy;

import com.mesosphere.sdk.scheduler.plan.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * A CanaryStrategy blocks deployment entirely by default until human intervention through the {@link #proceed()} call.
 * After a single {@link #proceed()} call it again blocks until human intervention.  Any further calls to
 * {@link #proceed()} will indicate the strategy should continue without further intervention.
 *
 * @param <C> is the type of {@link Element}s to which the Strategy applies.
 */
@SuppressWarnings("rawtypes")
public class CanaryStrategy<C extends Element> extends SerialStrategy<C> {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private AtomicBoolean initialized = new AtomicBoolean(false);
    private List<Element> children;

    @Override
    public Collection<C> getCandidates(Element<C> parentElement, Collection<String> dirtyAssets) {
        if (!initialized.get()) {
            children = parentElement.getChildren().stream()
                    .filter(element -> (element.isPending() || element.isWaiting()))
                    .collect(Collectors.toList());

            for (int i = 0; i < 2 && i < children.size(); i++) {
                children.get(i).getStrategy().interrupt();
            }

            initialized.set(true);
        }

        return super.getCandidates(parentElement, dirtyAssets);
    }

    @Override
    public void proceed() {
        super.proceed();
        
        if (!initialized.get())  {
            logger.warn("Proceed has no effect to children before strategy initialization.");
            return;
        }

        Optional<Element> elementOptional = children.stream()
                .filter(element -> element.getStrategy().isInterrupted())
                .findFirst();

        if (elementOptional.isPresent()) {
            elementOptional.get().getStrategy().proceed();
        }
    }
}
