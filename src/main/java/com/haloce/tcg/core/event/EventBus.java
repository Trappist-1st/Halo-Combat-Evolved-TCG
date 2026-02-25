package com.haloce.tcg.core.event;

public interface EventBus {
    void publish(GameEvent event);

    void register(EventListener listener);

    void processQueue();
}
