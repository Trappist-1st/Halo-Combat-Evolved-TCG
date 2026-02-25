package com.haloce.tcg.core.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EventContext {
    private final EventBus eventBus;
    private final Map<String, Object> attributes = new HashMap<>();

    public EventContext(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public void put(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) attributes.get(key);
    }

    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(attributes);
    }
}
