package com.haloce.tcg.game;

public class TurnExecutor {
    private final GameStateManager gameStateManager;

    public TurnExecutor(GameStateManager gameStateManager) {
        this.gameStateManager = gameStateManager;
    }

    public void advancePhase() {
        gameStateManager.advancePhaseInternal();
    }
}
