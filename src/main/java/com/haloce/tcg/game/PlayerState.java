package com.haloce.tcg.game;

import com.haloce.tcg.card.runtime.CardInstance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

public class PlayerState {
    private static final int MAX_SUPPLY_CAP = 10;

    private final String playerId;
    private final Deque<CardInstance> library;
    private final List<CardInstance> hand = new ArrayList<>();
    private final List<CardInstance> discardPile = new ArrayList<>();

    private int baseHealth;
    private int supplyCap;
    private int currentSupply;
    private int battery;
    private boolean batteryConvertedThisTurn;
    private int controlledLaneCount;
    private int fullControlStreak;

    public PlayerState(String playerId, int baseHealth, List<CardInstance> deck) {
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId is required");
        }
        if (baseHealth <= 0) {
            throw new IllegalArgumentException("baseHealth must be > 0");
        }
        this.playerId = playerId;
        this.baseHealth = baseHealth;
        this.library = new ArrayDeque<>(deck);
    }

    public String playerId() {
        return playerId;
    }

    public int baseHealth() {
        return baseHealth;
    }

    public int supplyCap() {
        return supplyCap;
    }

    public int currentSupply() {
        return currentSupply;
    }

    public int battery() {
        return battery;
    }

    public int handSize() {
        return hand.size();
    }

    public int librarySize() {
        return library.size();
    }

    public int discardSize() {
        return discardPile.size();
    }

    public int controlledLaneCount() {
        return controlledLaneCount;
    }

    public int fullControlStreak() {
        return fullControlStreak;
    }

    public List<CardInstance> hand() {
        return List.copyOf(hand);
    }

    public void startTurnResourceStep() {
        supplyCap = Math.min(MAX_SUPPLY_CAP, supplyCap + 1);
        currentSupply = supplyCap;
        batteryConvertedThisTurn = false;
    }

    public void setStartingSupplyCap(int startingSupplyCap) {
        this.supplyCap = Math.max(0, Math.min(MAX_SUPPLY_CAP, startingSupplyCap));
        this.currentSupply = this.supplyCap;
    }

    public void grantSupply(int amount) {
        if (amount <= 0) {
            return;
        }
        currentSupply = Math.min(supplyCap, currentSupply + amount);
    }

    public boolean consumeSupply(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (currentSupply < amount) {
            return false;
        }
        currentSupply -= amount;
        return true;
    }

    public List<CardInstance> draw(int count) {
        List<CardInstance> drawn = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CardInstance card = library.pollFirst();
            if (card == null) {
                break;
            }
            hand.add(card);
            drawn.add(card);
        }
        return drawn;
    }

    public Optional<CardInstance> findHandCard(String instanceId) {
        return hand.stream().filter(card -> card.instanceId().equals(instanceId)).findFirst();
    }

    public Optional<CardInstance> removeFromHand(String instanceId) {
        Optional<CardInstance> cardOpt = findHandCard(instanceId);
        cardOpt.ifPresent(hand::remove);
        return cardOpt;
    }

    public boolean spendResources(int supplyCost, int batteryCost) {
        if (supplyCost < 0 || batteryCost < 0) {
            throw new IllegalArgumentException("Resource cost must be >= 0");
        }
        if (currentSupply < supplyCost || battery < batteryCost) {
            return false;
        }
        currentSupply -= supplyCost;
        battery -= batteryCost;
        return true;
    }

    public void convertHandCardToBattery(String instanceId) {
        if (batteryConvertedThisTurn) {
            throw new IllegalStateException("Battery conversion already used this turn");
        }
        CardInstance card = removeFromHand(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Card not found in hand: " + instanceId));
        discardPile.add(card);
        battery += 1;
        batteryConvertedThisTurn = true;
    }

    public void putToDiscard(CardInstance card) {
        discardPile.add(card);
    }

    public void applyBaseDamage(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Damage must be >= 0");
        }
        baseHealth = Math.max(0, baseHealth - amount);
    }

    public void setLaneControl(int laneCount) {
        this.controlledLaneCount = Math.max(0, laneCount);
        if (controlledLaneCount == Lane.values().length) {
            fullControlStreak += 1;
        } else {
            fullControlStreak = 0;
        }
    }
}
