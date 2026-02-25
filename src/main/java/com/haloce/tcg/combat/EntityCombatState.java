package com.haloce.tcg.combat;

public class EntityCombatState {
    private int currentShield;
    private int currentHealth;
    private int coverValue;
    private boolean marked;
    private boolean suppressed;

    public EntityCombatState(int currentShield, int currentHealth) {
        this.currentShield = currentShield;
        this.currentHealth = currentHealth;
    }

    public int currentShield() {
        return currentShield;
    }

    public int currentHealth() {
        return currentHealth;
    }

    public int coverValue() {
        return coverValue;
    }

    public boolean marked() {
        return marked;
    }

    public boolean suppressed() {
        return suppressed;
    }

    public void setCoverValue(int coverValue) {
        this.coverValue = Math.max(0, coverValue);
    }

    public void setMarked(boolean marked) {
        this.marked = marked;
    }

    public void setSuppressed(boolean suppressed) {
        this.suppressed = suppressed;
    }

    public int applyShieldDamage(int damage) {
        int effective = Math.max(0, damage);
        int absorbed = Math.min(currentShield, effective);
        currentShield -= absorbed;
        return effective - absorbed;
    }

    public int applyHealthDamage(int damage) {
        int effective = Math.max(0, damage);
        int before = currentHealth;
        currentHealth = Math.max(0, currentHealth - effective);
        return before - currentHealth;
    }

    public void rechargeShieldTo(int shieldCap) {
        currentShield = Math.max(0, shieldCap);
    }

    public int healHealth(int amount, int healthCap) {
        int effective = Math.max(0, amount);
        int before = currentHealth;
        currentHealth = Math.min(Math.max(0, healthCap), currentHealth + effective);
        return currentHealth - before;
    }
}
