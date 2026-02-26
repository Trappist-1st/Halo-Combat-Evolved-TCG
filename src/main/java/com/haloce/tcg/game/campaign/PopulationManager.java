package com.haloce.tcg.game.campaign;

import com.haloce.tcg.game.Lane;

import java.util.EnumMap;
import java.util.Map;

/**
 * Manages unit population limits for both Ground and Orbital layers.
 */
public class PopulationManager {
    private final int maxPop;
    private int currentPop;
    
    // Per-player, per-lane, per-layer slot usage
    private final Map<Lane, Map<LaneLayer, Integer>> slotUsage = new EnumMap<>(Lane.class);

    public PopulationManager(int maxPop) {
        this.maxPop = maxPop;
        this.currentPop = 0;
        
        for (Lane lane : Lane.values()) {
            Map<LaneLayer, Integer> usage = new EnumMap<>(LaneLayer.class);
            usage.put(LaneLayer.GROUND, 0);
            usage.put(LaneLayer.ORBIT, 0);
            slotUsage.put(lane, usage);
        }
    }

    public boolean canAddUnit(int popValue, Lane lane, boolean isOrbital) {
        if (currentPop + popValue > maxPop) {
            return false;
        }
        
        LaneLayer layer = isOrbital ? LaneLayer.ORBIT : LaneLayer.GROUND;
        int maxSlots = slotCapacity(layer);
        
        return slotUsage.get(lane).get(layer) < maxSlots;
    }

    public void addUnit(int popValue, Lane lane, boolean isOrbital) {
        currentPop += popValue;
        LaneLayer layer = isOrbital ? LaneLayer.ORBIT : LaneLayer.GROUND;
        slotUsage.get(lane).merge(layer, 1, Integer::sum);
    }
    
    public void removeUnit(int popValue, Lane lane, boolean isOrbital) {
        currentPop = Math.max(0, currentPop - popValue);
        LaneLayer layer = isOrbital ? LaneLayer.ORBIT : LaneLayer.GROUND;
        slotUsage.get(lane).merge(layer, -1, (old, val) -> Math.max(0, old + val));
    }

    public int count(Lane lane, LaneLayer layer) {
        return slotUsage.get(lane).get(layer);
    }

    public int countGround(Lane lane) {
        return count(lane, LaneLayer.GROUND);
    }

    public int countOrbit(Lane lane) {
        return count(lane, LaneLayer.ORBIT);
    }

    public int remainingSlots(Lane lane, LaneLayer layer) {
        return Math.max(0, slotCapacity(layer) - count(lane, layer));
    }

    public int slotCapacity(LaneLayer layer) {
        return layer == LaneLayer.ORBIT ? 2 : 4;
    }

    public int currentPop() { return currentPop; }
    public int maxPop() { return maxPop; }
}
