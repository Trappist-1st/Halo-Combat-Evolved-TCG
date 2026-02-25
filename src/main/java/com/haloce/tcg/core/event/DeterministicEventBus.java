package com.haloce.tcg.core.event;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public class DeterministicEventBus implements EventBus {
    private final Deque<GameEvent> queue = new ArrayDeque<>();
    private final List<EventListener> listeners = new ArrayList<>();
    private final EventContext context;

    public DeterministicEventBus() {
        this.context = new EventContext(this);
    }

    @Override
    public void publish(GameEvent event) {
        queue.addLast(event);
    }

    @Override
    public void register(EventListener listener) {
        listeners.add(listener);
        listeners.sort(Comparator.comparingInt(EventListener::priority));
    }

    @Override
    public void processQueue() {
        while (!queue.isEmpty()) {
            GameEvent event = queue.removeFirst();
            List<EventListener> matched = listeners.stream()
                    .filter(listener -> listener.supports() == event.type())
                    .collect(Collectors.toList());

            for (EventListener listener : matched) {
                listener.onEvent(event, context);
            }
        }
    }

    public EventContext context() {
        return context;
    }
}
