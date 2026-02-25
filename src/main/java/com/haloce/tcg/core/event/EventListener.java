package com.haloce.tcg.core.event;

public interface EventListener {
    EventType supports();

    int priority();

    void onEvent(GameEvent event, EventContext context);
}
