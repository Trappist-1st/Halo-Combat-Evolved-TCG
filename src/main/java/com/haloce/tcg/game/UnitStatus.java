package com.haloce.tcg.game;

public class UnitStatus {
    private int summonedTurnIndex = -1;
    private int plasmaTaggedTurnIndex = -1;
    private int noobComboTriggeredTurnIndex = -1;
    private int attackedTurnIndex = -1;
    private int damagedTurnIndex = -1;
    private String damagedByPlayerId;
    private int cannotAttackUntilTurn = -1;
    private int cannotMoveUntilTurn = -1;
    private boolean hasCamoThisTurn;

    public int summonedTurnIndex() {
        return summonedTurnIndex;
    }

    public void setSummonedTurnIndex(int summonedTurnIndex) {
        this.summonedTurnIndex = summonedTurnIndex;
    }

    public int plasmaTaggedTurnIndex() {
        return plasmaTaggedTurnIndex;
    }

    public void setPlasmaTaggedTurnIndex(int plasmaTaggedTurnIndex) {
        this.plasmaTaggedTurnIndex = plasmaTaggedTurnIndex;
    }

    public int noobComboTriggeredTurnIndex() {
        return noobComboTriggeredTurnIndex;
    }

    public void setNoobComboTriggeredTurnIndex(int noobComboTriggeredTurnIndex) {
        this.noobComboTriggeredTurnIndex = noobComboTriggeredTurnIndex;
    }

    public int attackedTurnIndex() {
        return attackedTurnIndex;
    }

    public void setAttackedTurnIndex(int attackedTurnIndex) {
        this.attackedTurnIndex = attackedTurnIndex;
    }

    public int damagedTurnIndex() {
        return damagedTurnIndex;
    }

    public String damagedByPlayerId() {
        return damagedByPlayerId;
    }

    public void markDamaged(int turnIndex, String byPlayerId) {
        this.damagedTurnIndex = turnIndex;
        this.damagedByPlayerId = byPlayerId;
    }

    public int cannotAttackUntilTurn() {
        return cannotAttackUntilTurn;
    }

    public void setCannotAttackUntilTurn(int cannotAttackUntilTurn) {
        this.cannotAttackUntilTurn = cannotAttackUntilTurn;
    }

    public int cannotMoveUntilTurn() {
        return cannotMoveUntilTurn;
    }

    public void setCannotMoveUntilTurn(int cannotMoveUntilTurn) {
        this.cannotMoveUntilTurn = cannotMoveUntilTurn;
    }

    public boolean hasCamoThisTurn() {
        return hasCamoThisTurn;
    }

    public void setHasCamoThisTurn(boolean hasCamoThisTurn) {
        this.hasCamoThisTurn = hasCamoThisTurn;
    }

    public boolean damagedLastOpponentTurn(String ownerPlayerId, int currentTurnIndex) {
        return damagedTurnIndex == currentTurnIndex - 1
                && damagedByPlayerId != null
                && !damagedByPlayerId.equals(ownerPlayerId);
    }
}
