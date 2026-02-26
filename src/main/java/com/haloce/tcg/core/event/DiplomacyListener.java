package com.haloce.tcg.core.event;

import java.util.List;

public class DiplomacyListener implements EventListener {
    private final EventType subscribedType;
    private final EventReactionRegistry registry;

    public DiplomacyListener(EventType subscribedType, EventReactionRegistry registry) {
        this.subscribedType = subscribedType;
        this.registry = registry;
    }

    @Override
    public EventType supports() {
        return subscribedType;
    }

    @Override
    public int priority() {
        return 300;
    }

    @Override
    public void onEvent(GameEvent event, EventContext context) {
        registry.onEvent(event, context);
    }

    public static List<EventListener> defaultListeners(EventReactionRegistry registry) {
        return List.of(
                new DiplomacyListener(EventType.TURN_STARTED, registry),
                new DiplomacyListener(EventType.TURN_ENDED, registry),
                new DiplomacyListener(EventType.KILL_OCCURRED, registry),
                new DiplomacyListener(EventType.INFECT_TRIGGERED, registry),
                new DiplomacyListener(EventType.ATTACK_DECLARED, registry)
        );
    }
}
